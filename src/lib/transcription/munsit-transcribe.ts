import { z } from "zod";

/**
 * Munsit (Arabic ASR) file transcription.
 *
 * Munsit is NOT OpenAI-compatible: it authenticates with an `x-api-key`
 * header (not `Authorization: Bearer`), the endpoint is
 * `POST /api/v1/audio/transcribe` (not `/audio/transcriptions`), and the
 * transcript comes back wrapped as `{ statusCode, data: { transcription } }`.
 * Pointing the OpenAI SDK ("Custom" provider) at it therefore always fails.
 *
 * Docs: https://docs.munsit.com/speech-to-text/transcribe
 */

export const MUNSIT_DEFAULT_BASE_URL = "https://api.munsit.com/api/v1";

const MAX_TRANSCRIPT_CHARS = 2_000_000;
const MAX_UTTERANCES = 100_000;

export class MunsitTranscribeError extends Error {
    constructor(
        public status: number,
        message: string,
        public errorCode?: number,
    ) {
        super(message);
        this.name = "MunsitTranscribeError";
    }
}

const munsitUtteranceSchema = z.object({
    speaker: z.number().int().min(0),
    text: z.string().max(MAX_TRANSCRIPT_CHARS),
});

const munsitResponseSchema = z.object({
    data: z.object({
        transcription: z.string().max(MAX_TRANSCRIPT_CHARS),
        utterances: z
            .array(munsitUtteranceSchema)
            .max(MAX_UTTERANCES)
            .optional(),
        diarization: z
            .object({ status: z.string().max(32) })
            .partial()
            .optional(),
    }),
});

const munsitErrorSchema = z.object({
    errorCode: z.number().optional(),
    errorMessage: z.string().max(2000).optional(),
    message: z.string().max(2000).optional(),
});

export interface MunsitTranscribeArgs {
    apiKey: string;
    /** `munsit` (Arabic) or `munsit-en-ar` (Arabic/English code-switching). */
    model: string;
    file: File;
    baseUrl?: string | null;
    language?: string;
    diarize?: boolean;
    timeoutMs?: number;
}

export interface MunsitTranscribeResult {
    text: string;
    detectedLanguage: string | null;
}

/**
 * Accepts the base URL with or without the `/api/v1` suffix, so both
 * `https://api.munsit.com` and `https://api.munsit.com/api/v1` work.
 */
export function resolveMunsitBaseUrl(baseUrl?: string | null): string {
    const trimmed = (baseUrl ?? "").trim().replace(/\/+$/, "");
    if (!trimmed) return MUNSIT_DEFAULT_BASE_URL;
    return /\/api\/v1$/.test(trimmed) ? trimmed : `${trimmed}/api/v1`;
}

/**
 * Formats Munsit utterances as `Speaker N: ...` lines (1-based), the same
 * shape the ElevenLabs and Plaud paths produce for the transcript panel.
 */
export function formatMunsitUtterances(
    utterances: z.infer<typeof munsitUtteranceSchema>[],
): string {
    const lines: { speaker: number; text: string }[] = [];
    for (const u of utterances) {
        const text = u.text.trim();
        if (!text) continue;
        const speaker = u.speaker + 1;
        const last = lines.at(-1);
        if (last && last.speaker === speaker) {
            last.text += ` ${text}`;
        } else {
            lines.push({ speaker, text });
        }
    }
    return lines
        .map((line) => `Speaker ${line.speaker}: ${line.text}`)
        .join("\n");
}

function munsitLanguage(model: string, language?: string): string | null {
    // `munsit` is Arabic only and ignores `language`.
    if (model !== "munsit-en-ar") return "ar";
    const lower = language?.toLowerCase().split(/[-_]/)[0];
    return lower === "ar" || lower === "en" ? lower : null;
}

export async function munsitTranscribe({
    apiKey,
    model,
    file,
    baseUrl,
    language,
    diarize,
    timeoutMs,
}: MunsitTranscribeArgs): Promise<MunsitTranscribeResult> {
    const resolvedModel = model || "munsit";
    const lang = munsitLanguage(resolvedModel, language);

    const form = new FormData();
    form.append("file", file, file.name);
    form.append("model", resolvedModel);
    form.append("return_timestamps", "false");
    if (resolvedModel === "munsit-en-ar" && lang) {
        form.append("language", lang);
    }
    if (diarize) {
        form.append("diarize", "true");
    }

    const response = await fetch(
        `${resolveMunsitBaseUrl(baseUrl)}/audio/transcribe`,
        {
            method: "POST",
            headers: { "x-api-key": apiKey },
            body: form,
            signal: timeoutMs ? AbortSignal.timeout(timeoutMs) : undefined,
            redirect: "error",
        },
    );

    const raw = await response.text();
    let body: unknown = null;
    try {
        body = raw ? JSON.parse(raw) : null;
    } catch {
        body = null;
    }

    if (!response.ok) {
        const parsedError = munsitErrorSchema.safeParse(body);
        const detail = parsedError.success
            ? (parsedError.data.errorMessage ?? parsedError.data.message)
            : undefined;
        throw new MunsitTranscribeError(
            response.status,
            `Munsit transcription failed (HTTP ${response.status})${
                detail ? `: ${detail}` : ""
            }`,
            parsedError.success ? parsedError.data.errorCode : undefined,
        );
    }

    const parsed = munsitResponseSchema.safeParse(body);
    if (!parsed.success) {
        throw new MunsitTranscribeError(
            response.status,
            "Munsit returned an unexpected response (no data.transcription).",
        );
    }

    const { transcription, utterances, diarization } = parsed.data.data;
    const text =
        diarize && diarization?.status === "ok" && utterances?.length
            ? formatMunsitUtterances(utterances)
            : transcription.trim();

    if (!text) {
        throw new MunsitTranscribeError(
            response.status,
            "Munsit returned an empty transcription. The audio may be silent.",
        );
    }

    return { text, detectedLanguage: lang };
}

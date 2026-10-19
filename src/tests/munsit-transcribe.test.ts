import { afterEach, describe, expect, it, vi } from "vitest";
import { getTranscriptionStyle } from "@/lib/ai/provider-presets";
import {
    MunsitTranscribeError,
    munsitTranscribe,
    resolveMunsitBaseUrl,
} from "@/lib/transcription/munsit-transcribe";

const file = new File([new Uint8Array([1, 2, 3])], "a.mp3", {
    type: "audio/mpeg",
});

function mockFetch(status: number, body: unknown) {
    const fn = vi.fn(
        async () =>
            new Response(JSON.stringify(body), {
                status,
                headers: { "content-type": "application/json" },
            }),
    );
    vi.stubGlobal("fetch", fn);
    return fn;
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe("Munsit transcription", () => {
    it("routes the Munsit preset to the munsit style", () => {
        expect(getTranscriptionStyle("Munsit")).toBe("munsit");
    });

    it("normalizes the base URL", () => {
        expect(resolveMunsitBaseUrl(null)).toBe(
            "https://api.munsit.com/api/v1",
        );
        expect(resolveMunsitBaseUrl("https://api.munsit.com/")).toBe(
            "https://api.munsit.com/api/v1",
        );
        expect(resolveMunsitBaseUrl("https://api.munsit.com/api/v1/")).toBe(
            "https://api.munsit.com/api/v1",
        );
    });

    it("sends x-api-key to /audio/transcribe and reads data.transcription", async () => {
        const fetchMock = mockFetch(200, {
            statusCode: 200,
            data: { transcription: " اجتماع الفريق " },
            message: "Success",
        });

        const result = await munsitTranscribe({
            apiKey: "k",
            model: "munsit",
            file,
        });

        expect(result).toEqual({
            text: "اجتماع الفريق",
            detectedLanguage: "ar",
        });
        const [url, init] = fetchMock.mock.calls[0] as unknown as [
            string,
            RequestInit,
        ];
        expect(url).toBe("https://api.munsit.com/api/v1/audio/transcribe");
        expect(init.headers).toEqual({ "x-api-key": "k" });
        const form = init.body as FormData;
        expect(form.get("model")).toBe("munsit");
        expect(form.get("file")).toBeInstanceOf(File);
    });

    it("formats diarized utterances as Speaker N lines", async () => {
        mockFetch(200, {
            data: {
                transcription: "أهلا كيف حالك بخير شكرا",
                utterances: [
                    { speaker: 0, start: 0, end: 1, text: "أهلا" },
                    { speaker: 0, start: 1, end: 2, text: "كيف حالك" },
                    { speaker: 1, start: 3, end: 4, text: "بخير شكرا" },
                ],
                diarization: { status: "ok", speaker_cap_reached: false },
            },
        });

        const result = await munsitTranscribe({
            apiKey: "k",
            model: "munsit",
            file,
            diarize: true,
        });

        expect(result.text).toBe(
            "Speaker 1: أهلا كيف حالك\nSpeaker 2: بخير شكرا",
        );
    });

    it("surfaces Munsit error messages", async () => {
        mockFetch(401, { errorCode: 40101, errorMessage: "Invalid API key" });

        await expect(
            munsitTranscribe({ apiKey: "bad", model: "munsit", file }),
        ).rejects.toThrow(MunsitTranscribeError);
        await expect(
            munsitTranscribe({ apiKey: "bad", model: "munsit", file }),
        ).rejects.toThrow(/HTTP 401.*Invalid API key/);
    });
});

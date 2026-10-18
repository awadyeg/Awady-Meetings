import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { readFileSync } from "node:fs";

const token = readFileSync("/tmp/mcp-interop/token", "utf8").trim();
const url = "http://127.0.0.1:18765/mcp";
const results = {};

async function exercise(label, transport) {
  const client = new Client({ name: "interop-" + label, version: "1.0.0" });
  await client.connect(transport);
  const info = client.getServerVersion();
  const tools = await client.listTools();
  const list = await client.callTool({ name: "list_recordings", arguments: { type: "call", limit: 5 } });
  const person = await client.callTool({ name: "get_person", arguments: { name: "احمد" } });
  const search = await client.callTool({ name: "search_recordings", arguments: { query: "تسليم الخميس" } });
  const rec = await client.callTool({ name: "get_recording", arguments: { id: "m1" } });
  const tasks = await client.callTool({ name: "list_tasks", arguments: { person: "Sara Ali" } });
  const ask = await client.callTool({ name: "ask", arguments: { question: "What was decided?", person: "sara" } });
  const missing = await client.callTool({ name: "get_recording", arguments: { id: "nope" } });
  await client.ping();
  results[label] = {
    server: info,
    negotiated: transport.protocolVersion ?? null,
    tools: tools.tools.map(t => t.name),
    calls: list.structuredContent.recordings.map(r => r.id),
    person: [person.structuredContent.name, person.structuredContent.phone],
    searchHit: search.structuredContent.results.map(r => r.id),
    transcriptLines: rec.structuredContent.transcript.length,
    notes: rec.structuredContent.notes.map(n => n.note),
    sarasTasks: tasks.structuredContent.tasks.map(t => t.id),
    askAnswer: ask.structuredContent.answer,
    missingIsError: missing.isError === true,
  };
  await client.close();
}

// 1. Native Streamable HTTP with the bearer header (Cursor / Claude Code style config).
await exercise("http", new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: "Bearer " + token } } }));

// 2. Exactly the mcp-remote config the app's "Copy config JSON" produces (Claude Desktop style).
await exercise("mcp-remote", new StdioClientTransport({
  command: "node",
  args: ["node_modules/mcp-remote/dist/proxy.js", url, "--allow-http", "--transport", "http-only", "--header", "Authorization:${RIFFADO_AUTH}"],
  env: { ...process.env, RIFFADO_AUTH: "Bearer " + token },
  stderr: "ignore",
}));

// 3. Wrong token must be refused.
try {
  await exercise("bad-token", new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: "Bearer rfd_wrong" } } }));
  results["bad-token"] = "UNEXPECTEDLY CONNECTED";
} catch (e) {
  results["bad-token"] = "refused: " + String(e.message || e).slice(0, 120);
}
console.log(JSON.stringify(results, null, 2));

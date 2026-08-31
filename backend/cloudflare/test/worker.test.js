import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/worker.js";

const installation = "5e16cd9a-64f7-4f2d-b16f-4f2b6f0c7f21";
const env = {
  LIVEKIT_URL: "wss://jarvis-test.livekit.cloud",
  LIVEKIT_API_KEY: "APIkey123",
  LIVEKIT_API_SECRET: "a-secure-livekit-test-secret",
  JARVIS_ALLOWED_INSTALLATIONS: installation,
};

test("rejects a token request for an unknown installation", async () => {
  const response = await worker.fetch(new Request("https://token.example/v1/livekit/token"), env);
  assert.equal(response.status, 401);
});

test("mints only a short-lived, agent-dispatched token for an allowed installation", async () => {
  const response = await worker.fetch(new Request("https://token.example/v1/livekit/token", {
    headers: { "X-Jarvis-Installation": installation },
  }), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.serverUrl, env.LIVEKIT_URL);
  const payload = JSON.parse(Buffer.from(body.participantToken.split(".")[1], "base64url").toString());
  assert.equal(payload.sub, `android-${installation}`);
  assert.equal(payload.exp - payload.iat, 600);
  assert.equal(payload.roomConfig.agents[0].agentName, "jarvis-mobile");
});

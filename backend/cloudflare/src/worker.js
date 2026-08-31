const AGENT_NAME = "jarvis-mobile";
const INSTALLATION_PATTERN = /^[0-9a-f]{8}-[0-9a-f-]{27,36}$/i;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function base64Url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function createLiveKitToken(env, installation) {
  const now = Math.floor(Date.now() / 1000);
  const room = `jarvis-${installation.replace(/-/g, "").slice(0, 20)}`;
  const header = base64Url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64Url(JSON.stringify({
    iss: env.LIVEKIT_API_KEY,
    sub: `android-${installation}`,
    name: "JARVIS Mobile",
    iat: now,
    nbf: now - 5,
    exp: now + 600,
    video: {
      roomJoin: true,
      room,
      canPublish: true,
      canSubscribe: true,
      canPublishData: true,
    },
    roomConfig: { agents: [{ agentName: AGENT_NAME }] },
  }));
  const signingInput = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(env.LIVEKIT_API_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(signingInput));
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

function allowedInstallations(env) {
  return new Set((env.JARVIS_ALLOWED_INSTALLATIONS || "").split(",").map((value) => value.trim()).filter(Boolean));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ service: "jarvis-token-service", status: "ready" });
    }
    if (request.method !== "GET" || url.pathname !== "/v1/livekit/token") {
      return json({ detail: "Not found" }, 404);
    }

    const installation = request.headers.get("X-Jarvis-Installation");
    if (!installation || !INSTALLATION_PATTERN.test(installation)) {
      return json({ detail: "Invalid installation identity" }, 401);
    }
    if (!allowedInstallations(env).has(installation)) {
      return json({ detail: "Installation is not authorized" }, 403);
    }
    if (!env.LIVEKIT_URL?.startsWith("wss://") || !env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET) {
      return json({ detail: "LiveKit service is not configured" }, 503);
    }

    return json({
      serverUrl: env.LIVEKIT_URL,
      participantToken: await createLiveKitToken(env, installation),
    });
  },
};

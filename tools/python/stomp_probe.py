"""Sonda mínima del protocolo (referencia): pide una sesión de invitado por REST, se conecta por STOMP con su token,
pide una partida y muestra el primer estado.
Uso:  pip install websockets   y luego   python3 stomp_probe.py [AGGRO|CONTROL] [host:puerto]
"""
import asyncio, json, sys, urllib.request
import websockets

NUL = "\x00"

def frame(cmd, headers=None, body=""):
    h = "".join(f"{k}:{v}\n" for k, v in (headers or {}).items())
    return f"{cmd}\n{h}\n{body}{NUL}"

def parse(raw):
    raw = raw.lstrip("\n")
    head, _, body = raw.partition("\n\n")
    lines = head.split("\n")
    return lines[0], dict(l.split(":", 1) for l in lines[1:] if ":" in l), body.rstrip(NUL)

def guest_session(host):
    """El servidor emite la identidad (id + token firmado). El cliente NUNCA se inventa su playerId."""
    req = urllib.request.Request(f"http://{host}/api/users/guest", method="POST")
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.load(r)

async def main():
    deck = sys.argv[1] if len(sys.argv) > 1 else "AGGRO"
    host = sys.argv[2] if len(sys.argv) > 2 else "localhost:8080"
    me = guest_session(host)
    pid = me["id"]
    async with websockets.connect(f"ws://{host}/timewarp-ws", subprotocols=["v12.stomp"]) as ws:
        # el token va en la cabecera Authorization del CONNECT; sin él el servidor rechaza la conexión
        await ws.send(frame("CONNECT", {"accept-version": "1.2", "host": "localhost", "heart-beat": "0,0",
                                        "Authorization": "Bearer " + me["token"]}))
        print("<-", parse(await ws.recv())[0])
        # 1) suscribirse ANTES de pedir la partida
        await ws.send(frame("SUBSCRIBE", {"id": "s0", "destination": f"/topic/player/{pid}"}))
        await ws.send(frame("SUBSCRIBE", {"id": "s1", "destination": f"/topic/player/{pid}/errors"}))
        await asyncio.sleep(0.2)
        # 2) pedir partida contra el bot
        await ws.send(frame("SEND", {"destination": "/app/game/startLocalMatch", "content-type": "application/json"},
                            json.dumps({"playerId": pid, "deckName": deck})))
        cmd, h, b = parse(await asyncio.wait_for(ws.recv(), 10))
        print("<-", cmd, h.get("destination"))
        print(json.dumps(json.loads(b), indent=2, ensure_ascii=False)[:2500])

asyncio.run(main())

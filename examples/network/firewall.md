# Firewall for the lobby port

Only the proxy may connect to a lobby. Anyone who reaches the lobby port directly bypasses the proxy:
behind Velocity they are refused (they cannot sign the forwarding data), behind BungeeCord without
BungeeGuard they could log in as anyone. So keep the lobby ports closed to the internet.

**Easiest:** run the proxy on the same machine and set `server.host: "127.0.0.1"` in the lobby's
config.yml. Nothing else can reach it, no firewall rule needed.

If the proxy runs on another machine (here `203.0.113.10`), allow only that address. The lobby ports in
this example are 25566 and 25567.

## Linux with ufw

```bash
sudo ufw allow 25565/tcp                                                # the proxy's public port (on the proxy machine)
sudo ufw allow from 203.0.113.10 to any port 25566:25567 proto tcp      # lobbies, only from the proxy
sudo ufw deny 25566:25567/tcp
```

## Linux with iptables

```bash
sudo iptables -A INPUT -p tcp --dport 25566:25567 -s 203.0.113.10 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 25566:25567 -j DROP
```

Save the rules (`iptables-save`, or `netfilter-persistent save` on Debian/Ubuntu) so they survive a reboot.

## Windows

```powershell
New-NetFirewallRule -DisplayName "Lobby from proxy" -Direction Inbound -Protocol TCP -LocalPort 25566-25567 -RemoteAddress 203.0.113.10 -Action Allow
```

Windows blocks inbound connections that no rule allows, so no deny rule is needed.

## Check it worked

From a machine that is not the proxy: `nc -vz <lobby-ip> 25566` (or Minecraft's "Direct connection" to
`<lobby-ip>:25566`) must fail. Through the proxy, joining still works.

## Hosting panels

On Pterodactyl or Pelican, give the lobby an allocation on an internal address (for example `127.0.0.1` or
the Docker network) that only the proxy uses, or ask your host to firewall the lobby port.

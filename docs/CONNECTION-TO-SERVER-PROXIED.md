# Connect to Server via a proxy

To connect to your Music Assistant server via a proxy, you need to have a proxy set up. The guide below covers one way to achieve this, but any proxy method will work.

## Setting up a reverse proxy for Music Assistant in Home Assistant OS

This guide uses the **DuckDNS** app for dynamic DNS and free Let's Encrypt certificates, and the **NGINX Home Assistant SSL proxy** app to proxy Music Assistant over TLS. Both run as official Home Assistant apps.

### Prerequisites

- Home Assistant OS with the **Supervisor** and **App store** available
- A free account at [duckdns.org](https://www.duckdns.org)
- Ports **80** and **443** forwarded on your router to your Home Assistant host's local IP

---

### Step 1 — Create a DuckDNS subdomain

1. Log in at [duckdns.org](https://www.duckdns.org) and create a subdomain, e.g. `myhome.duckdns.org`
2. Copy your **token** from the top of the DuckDNS dashboard — you will need it in the next step

---

### Step 2 — Install and configure the DuckDNS app

1. In Home Assistant go to **Settings → Apps → Install an app**
2. Find **DuckDNS** under the *Official apps* section and click **Install**
3. Go to the **Configuration** tab and fill in:

```yaml
   lets_encrypt:
     accept_terms: true
     certfile: fullchain.pem
     keyfile: privkey.pem
   token: YOUR_DUCKDNS_TOKEN
   domains:
     - myhome.duckdns.org
   seconds: 300
```

4. **Start** the app and check the **Log** tab — it should confirm the certificate was issued and saved to `/ssl/`

> The DuckDNS app keeps your subdomain pointed at your current public IP and automatically renews the Let's Encrypt certificate before it expires.

---

### Step 3 — Add a custom NGINX server block for Music Assistant

The NGINX app proxies Home Assistant by default. To also proxy Music Assistant, you need to add a custom server block. This is done by placing a configuration file in the `/share` directory.

Using the **File Editor** or **Studio Code Server** app, create the file `/share/nginx_proxy/music_assistant.conf` with the following content:

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 443 ssl http2;
    server_name myhome.duckdns.org;

    ssl_certificate     /ssl/fullchain.pem;
    ssl_certificate_key /ssl/privkey.pem;

    location /musicassistant/ {
        proxy_pass          http://localhost:8095/;
        proxy_http_version  1.1;
        proxy_set_header    Host              $host;
        proxy_set_header    X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header    X-Real-IP         $remote_addr;
        proxy_set_header    Upgrade           $http_upgrade;
        proxy_set_header    Connection        $connection_upgrade;
    }
}
```

> Replace `myhome.duckdns.org` with your actual DuckDNS subdomain.

---

### Step 4 — Install and configure the NGINX app

1. In Home Assistant go to **Settings → Apps → Install an app**
2. Find **NGINX Home Assistant SSL proxy** under the *Official apps* section and click **Install**
3. Go to the **Configuration** tab and set:

```yaml
   domain: myhome.duckdns.org
   certfile: fullchain.pem
   keyfile: privkey.pem
   hsts: "max-age=31536000; includeSubDomains"
   customize:
     active: true
     default: "nginx_proxy_default*.conf"
     servers: "nginx_proxy/*.conf"
```

4. **Start** the app and check the **Log** tab for any errors

---

### Step 5 — Trust the proxy in Home Assistant

Add the following to your `configuration.yaml`:

```yaml
http:
  use_x_forwarded_for: true
  trusted_proxies:
    - 172.30.33.0/24  # Default internal subnet used by HA apps
```

After saving, restart Home Assistant via **Settings → System → Restart**.

> If you see a `400 Bad Request` error when connecting through the proxy, this `trusted_proxies` entry is missing or incorrect.

---

### Step 6 — Connect Music Assistant

When configuring the Music Assistant server connection, use your DuckDNS address with the `/musicassistant/` path:

| Field | Value |
|---|---|
| **Server host** | `myhome.duckdns.org/musicassistant` |
| **Port** | `443` |
| **Use TLS (wss://)** | ✅ Enabled |

---

### Troubleshooting

**Let's Encrypt certificate not issued**
Make sure ports 80 and 443 are forwarded on your router to your HA host. Check the DuckDNS app log for details.

**WebSocket errors or stream drops**
Ensure the `map $http_upgrade` block and the `Upgrade`/`Connection` proxy headers are present in your custom NGINX config. Music Assistant requires a persistent WebSocket connection and will fail without them.

**Custom server block not loading**
Confirm the file is saved to `/share/nginx_proxy/music_assistant.conf` and that `customize.active` is set to `true` in the NGINX app configuration. Restart the NGINX app after any changes.

**Home Assistant rejects the connection**
Verify that `trusted_proxies` in `configuration.yaml` includes `172.30.33.0/24` and that HA has been fully restarted after the change.
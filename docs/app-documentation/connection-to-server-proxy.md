# Connect to Server over Proxy

To connect to your Music Assistant server via a proxy, you need to have a proxy set up. The guide below covers one way to achieve this using the HA **Duck DNS** integration and the **Nginx Proxy Manager** app, but any proxy method will work. 

Has your proxy been successfully set up? Then see the guide at the bottom of this article on how to connect the app via the setup proxy.

## What Duck DNS integration provides

It keeps a free `*.duckdns.org` subdomain pointed at your home's public IP address, even when it changes.

> Configure the Duck DNS integration on your Home Assistant installation by clicking the button below. You need to have a [Duck DNS account](https://www.duckdns.org/) for this with a setup subdomain and Duck DNS account token.

![Duck DNS integration setup](/docs/app-documentation/screenshots/connection-to-server-proxy/duck-dns-integration-setup.jpeg)

[![Add DuckDNS integration in Home Assistant](https://my.home-assistant.io/badges/config_flow_start.svg)](https://my.home-assistant.io/redirect/config_flow_start/?domain=duckdns)

## Setting up Nginx Proxy Manager

Nginx Proxy Manager lets you configure a reverse proxy and obtain the SSL certificate required to reach your Music Assistant server securely over `wss://`.

> Install the Nginx Proxy Manager add-on in your Home Assistant OS installation using the button below, then follow its documentation to complete the setup.

[![Install Nginx Proxy Manager in Home Assistant](https://my.home-assistant.io/badges/supervisor_addon.svg)](https://my.home-assistant.io/redirect/supervisor_addon/?addon=a0d7b954_nginxproxymanager&repository_url=https%3A%2F%2Fgithub.com%2Fhassio-addons%2Frepository)

Once installed, configure a proxy host for Music Assistant:

1. Open Nginx Proxy Manager
2. Navigate to **Hosts**
3. Click **Add Proxy Host**
4. On the **Details** tab, fill in the following fields:
   - **Domain Names** — your DuckDNS domain
   - **Forward Hostname / IP** — the IP address of your Music Assistant server
   - **Forward Port** — `8095` (default)
   - **Websocket Support** — enable this toggle
5. On the **SSL** tab, select **Request a new SSL Certificate** and complete the remaining fields.
6. Click **Save**. An SSL certificate will be issued and your Music Assistant proxy host will be ready.

![Nginx Proxy Details](/docs/app-documentation/screenshots/connection-to-server-proxy/proxy-setup-nginx-domain-host-port.jpeg)
![Nginx Proxy SSL](/docs/app-documentation/screenshots/connection-to-server-proxy/proxy-setup-nginx-ssl.jpeg)

## Fill in the Fields

| Field | Description |
|---|---|
| **Server host** | The hostname configured for your proxy (e.g. `ma-app.duckdns.org`). |
| **Port** | Data flows through port `443`; the proxy handles internal routing within your network. |
| **Use TLS (wss://)** | Since a Let's Encrypt SSL certificate is configured, enable this option to secure the connection to your server. |

![LAN connection via hostname](/docs/app-documentation/screenshots/connection-to-server-proxy/hostname.jpeg)

Once your details are filled in, tap **Connect** to move on to the next step.

## Authentication

After connecting, you will be asked to sign in. Choose one of the following methods:

| Authentication method | Description |
|---|---|
| **Music Assistant** | Sign in with the username and password of a Music Assistant user. |
| **Home Assistant** | Sign in using Home Assistant OAuth. The Home Assistant user must be linked to the Music Assistant server. |

![Sign in with Music Assistant credentials](/docs/app-documentation/screenshots/connection-to-server-proxy/ma-credentials.jpeg)
![Sign in using Home Assistant](/docs/app-documentation/screenshots/connection-to-server-proxy/sign-in-using-ha.jpeg)
![Fill in Home Assistant credentials](/docs/app-documentation/screenshots/connection-to-server-proxy/ha-sign-in-screen.jpeg)

Once signed in, you are ready to use the app.
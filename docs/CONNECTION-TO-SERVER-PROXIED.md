# Connect to Server via a proxy

To connect to your Music Assistant server via a proxy, you need to have a proxy set up. The guide below covers one way to achieve this using the HA **Duck DNS** integration and the **Nginx Proxy Manager** app, but any proxy method will work. 

Has your proxy been successfully set up? Then see the guide at the bottom of this article on how to connect the app via the setup proxy.

## What Duck DNS integration provides

It keeps a free `*.duckdns.org` subdomain pointed at your home's public IP address, even when it changes.

> Configure the Duck DNS integration on your Home Assistant installation by clicking the button below. You need to have a Duck DNS account for this with a setup subdomain and Duck DNS account token.

[![Add DuckDNS integration in Home Assistant](https://my.home-assistant.io/badges/config_flow_start.svg)](https://my.home-assistant.io/redirect/config_flow_start/?domain=duckdns)

## What Nginx Proxy Manager provides

The Nginx Proxy Manager allow you to setup to Proxy and request the SSL certificate needed to reach your Music Assistant server securely over `wss://`. However, DuckDNS alone does not expose port `8095` to the outside — for that you also need a reverse proxy such as the **NGINX Home Assistant SSL proxy** app (see the full setup guide below).

> Install the Nginx Proxy Manager app in your HA OS installation by using the button below, then follow its documentation to complete the setup.

[![Install DuckDNS in Home Assistant](https://my.home-assistant.io/badges/supervisor_addon.svg)](https://my.home-assistant.io/redirect/supervisor_addon/?addon=a0d7b954_nginxproxymanager&repository_url=https%3A%2F%2Fgithub.com%2Fhassio-addons%2Frepository)
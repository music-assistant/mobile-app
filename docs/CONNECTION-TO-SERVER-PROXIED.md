# Connect to Server via a proxy

To connect to your Music Assistant server via a proxy, you need to have a proxy set up. The guide below covers one way to achieve this using the **DuckDNS** and **NGINX Home Assistant SSL proxy** apps, but any proxy method will work. 

Has your proxy been successfully set up? Then see the guide at the bottom of this article on how to connect the app via the setup proxy.

## What DuckDNS provides

The DuckDNS app handles two things:

- It keeps a free `*.duckdns.org` subdomain pointed at your home's public IP address, even when it changes
- It automatically issues and renews a free Let's Encrypt TLS certificate for that subdomain

This gives you the domain name and certificate needed to reach your Music Assistant server securely over `wss://`. However, DuckDNS alone does not expose port `8095` to the outside — for that you also need a reverse proxy such as the **NGINX Home Assistant SSL proxy** app (see the full setup guide below).

## Install the DuckDNS app

Use the button below to install the DuckDNS app in your HA OS installation, then follow its documentation to complete the setup.

[![Install DuckDNS in Home Assistant](https://my.home-assistant.io/badges/supervisor_addon.svg)](https://my.home-assistant.io/redirect/supervisor_addon/?addon=core_duckdns)

> Once DuckDNS is running and your certificate has been issued, continue with the NGINX setup below to proxy Music Assistant traffic over TLS.
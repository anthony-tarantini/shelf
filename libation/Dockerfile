FROM ghcr.io/linuxserver/webtop:ubuntu-xfce

ARG TARGETARCH
ARG LIBATION_VERSION="13.3.5"

# 1. Install dependencies
RUN apt-get update && apt-get install -y \
    wget sudo libicu-dev libssl-dev libwebkit2gtk-4.1-0 \
    libasound2t64 libfontconfig1 libgtk-3-0t64 \
    && rm -rf /var/lib/apt/lists/*

# 2. Download and install Libation
RUN wget -O /tmp/libation.deb \
    https://github.com/rmcrackan/Libation/releases/download/v${LIBATION_VERSION}/Libation.${LIBATION_VERSION}-linux-chardonnay-${TARGETARCH}.deb \
    && apt-get update && apt-get install -y /tmp/libation.deb \
    && rm /tmp/libation.deb

# 3. Structural Permission Fix
RUN mkdir -p /config/Libation && \
    mkdir -p /config/.local/share && \
    ln -s /config/Libation /config/.local/share/Libation && \
    chown -R 911:911 /config

# 4. The Autostart (with the 'libation' case fix)
RUN mkdir -p /config/.config/autostart && \
    echo '[Desktop Entry]\n\
Type=Application\n\
Name=Libation\n\
Exec=libation\n\
OnlyShowIn=XFCE;\n\
StartupNotify=false\n\
Terminal=false\n\
Hidden=false' > /config/.config/autostart/libation.desktop && \
    chown -R 911:911 /config/.config

RUN mkdir -p /etc/custom-cont-init.d && \
    printf '#!/usr/bin/with-contenv bash\n\
chown -R abc:abc /config/Libation\n\
chmod -R 755 /config/Libation\n' > /etc/custom-cont-init.d/01-fix-permissions && \
    chmod +x /etc/custom-cont-init.d/01-fix-permissions

EXPOSE 3000
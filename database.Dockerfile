# Use the official Postgres image as a base
FROM postgres:16

# Install build dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    postgresql-server-dev-16 \
    git \
    && rm -rf /var/lib/apt/lists/*

# Clone and install the temporal_tables extension
RUN git clone https://github.com/arkhipov/temporal_tables.git /tmp/temporal_tables \
    && cd /tmp/temporal_tables \
    && make \
    && make install \
    && rm -rf /tmp/temporal_tables

# Clean up build dependencies to keep the image slim
RUN apt-get remove -y build-essential postgresql-server-dev-16 git && apt-get autoremove -y
FROM python:3.11-slim

RUN apt-get update && apt-get install -y \
    gcc \
    libffi-dev \
    libssl-dev \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN pip install "audible[cryptography]" fastapi uvicorn pydantic

WORKDIR /app
COPY main.py .

VOLUME /app/data

EXPOSE 8080

CMD ["python", "main.py"]

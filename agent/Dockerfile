FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --system jarvis \
    && useradd --system --gid jarvis --home-dir /app jarvis
COPY --chown=jarvis:jarvis main.py prompts.py ./

USER jarvis
CMD ["python", "main.py", "start"]

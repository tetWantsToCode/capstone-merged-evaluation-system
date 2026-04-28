# Gemini AI Setup Guide

The AI Assistant feature uses Google Gemini. You need to set up a `backend/.env` file with the API key.

## Quick Start

1. Copy the example env file: `cp backend/.env.example backend/.env`
2. Open `backend/.env` and fill in the actual values (ask your team lead for the keys).
3. Start the backend: `cd backend && ./mvnw spring-boot:run`
4. Start the frontend: `cd frontend && npm start`
5. Log in as a **Teacher** → go to **Reports** → use the AI chat on the right side.

## Getting a Gemini API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey).
2. Sign in with your Google account.
3. Click **Create API Key** and copy it.
4. Paste it in `backend/.env`:

```
GEMINI_API_KEY=your-api-key-here
GEMINI_MODEL=gemini-2.0-flash
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `GEMINI_API_KEY` | *(set in .env)* | Google Gemini API key |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Model to use (can be changed) |

### Available Models

| Model | Description |
|---|---|
| `gemini-2.0-flash` | Fast, good for most tasks |
| `gemini-2.5-flash-preview-05-20` | Latest preview with better reasoning |

Full list: [Google AI Models](https://ai.google.dev/gemini-api/docs/models)

## Troubleshooting

| Problem | Solution |
|---|---|
| AI not responding | Check that `GEMINI_API_KEY` is set in `backend/.env` |
| `403 Forbidden` | API key is disabled — get a new one from AI Studio |
| `404 Model not found` | Change `GEMINI_MODEL` to `gemini-2.0-flash` in `.env` |
| `429 Too Many Requests` | Rate limit hit — wait a minute or get a new key |

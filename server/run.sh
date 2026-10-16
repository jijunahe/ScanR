#!/bin/sh
cd "$(dirname "$0")"
python3 -m pip install -r requirements.txt
exec python3 -m uvicorn scanr_backend.main:app --host 0.0.0.0 --port 8000

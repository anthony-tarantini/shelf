import os
import json
import audible
import httpx
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI(title="Audible Sidecar for Shelf")

DATA_DIR = "/app/data"
AUTH_FILE = os.path.join(DATA_DIR, "auth.json")

# Ensure data directory exists
os.makedirs(DATA_DIR, exist_ok=True)

class FinalizeRequest(BaseModel):
    callbackUrl: str

def get_client():
    if not os.path.exists(AUTH_FILE):
        return None
    try:
        auth = audible.Authenticator.from_file(AUTH_FILE)
        return audible.Audible(auth)
    except:
        return None

@app.get("/auth/login-url")
async def get_login_url():
    # Use standard Audible Android client constants
    # The 'audible' library can generate this or we can provide it
    client_id = "amzn1.application-oa-client.8df46f88127042a99d63c5d63f9157ec"
    login_url = (
        "https://www.amazon.com/ap/signin?"
        "openid.oa2.client_id=device%3A" + client_id +
        "&openid.oa2.response_type=code"
        "&openid.oa2.scope=device_auth_access"
        "&openid.mode=checkid_setup"
        "&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0"
        "&openid.return_to=https%3A%2F%2Fwww.amazon.com%2Fap%2Fmaplanding"
        "&openid.assoc_handle=amzn_device_na"
    )
    return {"url": login_url}

@app.post("/auth/finalize")
async def finalize_auth(req: FinalizeRequest):
    try:
        # The library's from_login_url handles the exchange and device registration
        auth = audible.Authenticator.from_login_url(
            req.callbackUrl,
            client_id="amzn1.application-oa-client.8df46f88127042a99d63c5d63f9157ec"
        )
        auth.to_file(AUTH_FILE)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.get("/auth/status")
async def get_auth_status():
    client = get_client()
    return {
        "connected": client is not None,
        "username": "Connected Account" if client else None
    }

@app.get("/library")
async def list_library():
    client = get_client()
    if not client:
        raise HTTPException(status_code=401, detail="Audible not connected")
    
    try:
        library = client.get("library", num_results=100)
        items = library.get("items", [])
        
        result = []
        for item in items:
            result.append({
                "asin": item.get("asin"),
                "title": item.get("title"),
                "author": ", ".join([a.get("name") for a in item.get("authors", [])]),
                "type": "PODCAST" if item.get("content_metadata", {}).get("is_podcast") else "AUDIOBOOK",
                "imageUrl": item.get("product_images", {}).get("500") or item.get("product_images", {}).get("large")
            })
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/library/{asin}/feed")
async def get_feed(asin: str):
    client = get_client()
    if not client:
        raise HTTPException(status_code=401, detail="Audible not connected")
    
    try:
        product = client.get(f"catalog/products/{asin}", response_groups="product_desc,contributors,media,content")
        p = product.get("product", {})
        
        return {
            "title": p.get("title"),
            "description": p.get("publisher_summary"),
            "imageUrl": p.get("product_images", {}).get("500"),
            "episodes": [
                {
                    "guid": asin,
                    "title": p.get("title"),
                    "description": p.get("publisher_summary"),
                    "audioUrl": f"audible://{asin}",
                    "imageUrl": p.get("product_images", {}).get("500")
                }
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)

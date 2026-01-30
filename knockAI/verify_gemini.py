import os
import sys

# Ensure app directory is in path
sys.path.append(os.path.join(os.path.dirname(__file__), 'app'))

from services.ai_service import AIService
from dotenv import load_dotenv

def test_gemini_connection():
    print("Testing Gemini API Connection...")
    
    # Force reload env to ensure we get new keys if updated
    load_dotenv(override=True)
    
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("❌ Error: GEMINI_API_KEY not found in environment variables.")
        print("Please set GEMINI_API_KEY in your .env file or environment.")
        return

    print(f"✅ Found GEMINI_API_KEY: {api_key[:5]}...{api_key[-3:]}")

    service = AIService()
    
    if not service.client:
        print("❌ Error: AIService client not initialized.")
        return

    print(f"Testing with Model: {service.model}")
    print(f"Base URL: {service.client.base_url}")

    try:
        print("\n--- Listing Available Models (Flash) ---")
        models = service.client.models.list()
        flash_models = [m.id for m in models if "flash" in m.id.lower()]
        for mid in flash_models:
            print(f"- {mid}")
        print("-------------------------------\n")
    except Exception as e:
         print(f"❌ Failed to list models: {e}")

    try:
        response = service.analyze_news("Apple released a new iPhone today. Stock prices are expected to rise.")
        print("\n✅ API Call Successful!")
        print("Response Snippet:")
        print("-" * 50)
        print(response[:200] + "...")
        print("-" * 50)
    except Exception as e:
        print(f"\n❌ API Call Failed: {e}")

if __name__ == "__main__":
    test_gemini_connection()

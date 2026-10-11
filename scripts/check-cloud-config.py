#!/usr/bin/env python3
"""Local validation of configuration, never connects to a provider or prints secrets."""
import argparse
import base64
import os
import re
import sys
from pathlib import Path

PHASES = ("mysql", "mongo", "storage", "email", "sms", "payments", "render")
PLACEHOLDERS = ("YOUR_", "BASE64_", "CHANGE_ME", "EXAMPLE", "yourdomain.com", "app.example.com")

def parse_dotenv(file):
    result = {}
    if not file:
        return result
    path = Path(file)
    if not path.exists():
        print(f"ERROR: Cannot find environment file at {path}")
        sys.exit(2)
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            print(f"ERROR: Invalid key/value syntax at line {number}; use KEY=VALUE.")
            sys.exit(2)
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip().strip('"').strip("'")
    return result

def configured(value):
    return bool(value) and not any(marker.lower() in value.lower() for marker in PLACEHOLDERS)

def report(errors, warnings, ok, condition, title, detail=""):
    if condition:
        ok.append(title)
    else:
        errors.append(f"{title}: {detail}")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=PHASES+("all",), default="all")
    parser.add_argument("--env-file", help="Optional private KEY=VALUE file (never commit it)")
    args = parser.parse_args()
    config = dict(os.environ)
    config.update(parse_dotenv(args.env_file))
    v = lambda key: config.get(key, "").strip()
    errors, warnings, ok = [], [], []
    selected = PHASES if args.phase == "all" else (args.phase,)

    if "mysql" in selected:
        for key in ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"):
            report(errors,warnings,ok,configured(v(key)),key,"Set the Aiven connection setting in environment variables.")
        report(errors,warnings,ok,"sslmode=verify_identity" in v("SPRING_DATASOURCE_URL").lower(),
               "MySQL certificate and hostname validation", "Add sslMode=VERIFY_IDENTITY to the JDBC URL.")
        ca = v("AIVEN_CA_CERT_BASE64")
        valid_ca = False
        if configured(ca):
            try:
                decoded = base64.b64decode(ca, validate=True)
                valid_ca = b"-----BEGIN CERTIFICATE-----" in decoded
            except (ValueError, Exception):
                valid_ca = False
        report(errors,warnings,ok,valid_ca,"Aiven CA certificate", "Provide base64 PEM data as AIVEN_CA_CERT_BASE64 for Render Docker.")

    if "mongo" in selected:
        uri = v("MONGODB_URI")
        report(errors,warnings,ok,configured(uri) and uri.startswith("mongodb+srv://"),
               "MongoDB Atlas URI", "Use the Atlas mongodb+srv URI with a new rotated password.")
        if configured(uri) and "/bakir_messenger" not in uri:
            warnings.append("MongoDB URI: check that the bakir_messenger database is selected.")

    if "storage" in selected:
        valid = configured(v("CLOUDINARY_URL")) or all(configured(v(x)) for x in
              ("CLOUDINARY_CLOUD_NAME","CLOUDINARY_API_KEY","CLOUDINARY_API_SECRET"))
        report(errors,warnings,ok,valid,"Cloudinary credentials", "Provide CLOUDINARY_URL or the three Cloudinary components.")

    if "email" in selected:
        report(errors,warnings,ok,v("EMAIL_MODE").lower()=="resend", "Email production mode", "Set EMAIL_MODE=resend.")
        report(errors,warnings,ok,configured(v("RESEND_API_KEY")), "Resend API key", "Set RESEND_API_KEY.")
        report(errors,warnings,ok,configured(v("EMAIL_FROM")) and "@" in v("EMAIL_FROM"),
               "Verified sender address", "Set EMAIL_FROM at your verified sending domain.")

    if "sms" in selected:
        report(errors,warnings,ok,v("OTP_MODE").lower()=="twilio", "SMS production mode", "Set OTP_MODE=twilio.")
        report(errors,warnings,ok,v("OTP_FALLBACK_TO_DEMO").lower() in ("false","0","no"),
               "Demo fallback disabled", "Set OTP_FALLBACK_TO_DEMO=false.")
        for key in ("TWILIO_ACCOUNT_SID","TWILIO_AUTH_TOKEN","TWILIO_VERIFY_SERVICE_SID"):
            report(errors,warnings,ok,configured(v(key)),key,"Set the Twilio Verify credential.")

    if "payments" in selected:
        report(errors,warnings,ok,v("PAYMENT_MODE").lower()=="sandbox", "Payment sandbox mode", "Set PAYMENT_MODE=sandbox.")
        for key in ("SSLCOMMERZ_SANDBOX_STORE_ID","SSLCOMMERZ_SANDBOX_STORE_PASSWORD"):
            report(errors,warnings,ok,configured(v(key)),key,"Set your SSLCOMMERZ sandbox credential.")

    if "render" in selected:
        report(errors,warnings,ok,v("SPRING_PROFILES_ACTIVE")=="prod", "Production profile", "Set SPRING_PROFILES_ACTIVE=prod.")
        report(errors,warnings,ok,v("SECURE_COOKIE").lower()=="true", "Secure session cookie", "Set SECURE_COOKIE=true.")
        baseurl = v("APP_PUBLIC_BASE_URL")
        report(errors,warnings,ok,configured(baseurl) and baseurl.startswith("https://"),
               "Public HTTPS URL", "Use https://<your-render-host>.onrender.com or your custom app domain.")
        jwt = v("APP_JWT_SECRET")
        jwt_valid = False
        if configured(jwt):
            try:
                jwt_valid = len(base64.b64decode(jwt, validate=True)) >= 32
            except (ValueError, Exception):
                pass
        report(errors,warnings,ok,jwt_valid,"JWT signing secret", "Provide base64 of at least 32 random bytes.")
        report(errors,warnings,ok,configured(v("APP_RECEIPT_SECRET")) and len(v("APP_RECEIPT_SECRET").encode()) >= 32,
               "Independent receipt-signing secret", "Provide a separate random value of at least 32 bytes.")

    print(f"Cloud configuration check: {args.phase} (values never displayed)")
    print(f"Configured checks: {len(ok)}; missing/invalid: {len(errors)}; warnings: {len(warnings)}")
    for item in errors:
        print("MISSING: " + item)
    for item in warnings:
        print("WARNING: " + item)
    if not errors:
        print("Configuration looks ready for provider connection tests; no external API was contacted.")
    return 1 if errors else 0

if __name__ == "__main__":
    raise SystemExit(main())

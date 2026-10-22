#!/usr/bin/env python3
"""Shop backend for the benchmark app. The emulator reaches it at http://10.0.2.2:8787.

Usage: server.py [--port 8787] [--variant default|checkout-error|null-price]
"""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PRODUCTS = [
    {"id": 1, "name": "Blue mug", "price": 1200},
    {"id": 2, "name": "Notebook", "price": 450},
    {"id": 3, "name": "Travel mug", "price": 2400},
    {"id": 4, "name": "Pen set", "price": 800},
    {"id": 17, "name": "Gift mug", "price": 1800},
]


class Handler(BaseHTTPRequestHandler):
    # HTTP/1.1 keeps OkHttp's pooled connections valid between calls.
    protocol_version = "HTTP/1.1"
    variant = "default"

    def products(self):
        products = [dict(p) for p in PRODUCTS]
        if self.variant == "null-price":
            for p in products:
                if p["id"] == 17:
                    p["price"] = None
        return products

    def send_json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/products":
            return self.send_json(200, self.products())
        if url.path == "/search":
            query = parse_qs(url.query)
            text = query.get("q", [""])[0].lower()
            sort = query.get("sort", ["relevance"])[0]
            found = [p for p in self.products() if text in p["name"].lower()]
            # Unknown sort values are ignored, like the real backend.
            if sort == "price_desc":
                found.sort(key=lambda p: p["price"] or 0, reverse=True)
            elif sort == "price_asc":
                found.sort(key=lambda p: p["price"] or 0)
            return self.send_json(200, found)
        self.send_json(404, {"status": "error", "code": "NOT_FOUND", "message": "Unknown path"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        request = json.loads(self.rfile.read(length) or b"{}")
        if urlparse(self.path).path != "/checkout":
            return self.send_json(404, {"status": "error", "code": "NOT_FOUND", "message": "Unknown path"})
        if self.variant == "checkout-error" and request.get("coupon"):
            return self.send_json(200, {
                "status": "error",
                "code": "COUPON_EXPIRED",
                "message": "Coupon %s expired on 2026-09-30" % request["coupon"],
            })
        self.send_json(200, {"status": "ok", "orderId": "ORD-7731"})

    def log_message(self, fmt, *args):
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--variant", default="default", choices=["default", "checkout-error", "null-price"])
    args = parser.parse_args()
    Handler.variant = args.variant
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()

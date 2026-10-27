# API Guide

All routes are versioned under `/api/v1`. Requests and responses use JSON unless noted otherwise. Protected routes require `Authorization: Bearer <token>`.

For configuration, run modes, and security limitations, see [Configuration and operations](OPERATIONS.md). For implementation details, see [Architecture](ARCHITECTURE.md).

## Authentication workflow

1. `POST /api/v1/auth/register` creates a `CUSTOMER` account. Any attempted role in the payload is ignored.
2. `POST /api/v1/auth/login` returns a short-lived JWT access token.
3. Send `Authorization: Bearer <token>` on protected requests.
4. `POST /api/v1/auth/logout` stores the token's `jti` in `revoked_tokens`; later requests with that token are rejected. Expired denylist rows are removed hourly.
5. Disabling an account or changing a role takes effect on the next request because the validator and authority converter read current database state.
6. There are no refresh tokens; clients must log in again after expiry.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@nexacart.local","password":"Customer123!"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-10-27T02:00:00Z",
  "user": {
    "id": "f53e...",
    "email": "customer1@nexacart.local",
    "firstName": "Maya",
    "lastName": "Hassan",
    "role": "CUSTOMER",
    "enabled": true,
    "createdAt": "2026-10-27T01:30:00Z"
  }
}
```

## Endpoint overview

| Area | Method and path | Access |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login` | Public |
| Auth | `POST /auth/logout` | Authenticated |
| Users | `GET/PATCH /users/me` | Authenticated |
| Admin users | `GET /admin/users`, `GET/PATCH /admin/users/{id}` | ADMIN |
| Categories | `GET /categories`, `GET /categories/{id}` | Public (active only) |
| Admin categories | `GET /admin/categories`, `GET /admin/categories/{id}`, `POST /admin/categories`, `PUT /admin/categories/{id}`, `DELETE /admin/categories/{id}` | ADMIN |
| Products | `GET /products`, `GET /products/{id}` | Public (active only) |
| Admin products | `GET /admin/products`, `GET /admin/products/{id}`, `POST /admin/products`, `PUT /admin/products/{id}`, `DELETE /admin/products/{id}` | ADMIN |
| Reviews | `GET /products/{productId}/reviews` | Public |
| Reviews | `POST /products/{productId}/reviews`, `PATCH/DELETE /reviews/{id}` | Authenticated owner |
| Cart | `GET /cart`, `POST /cart/items`, `PATCH/DELETE /cart/items/{productId}`, `DELETE /cart` | Authenticated |
| Addresses | `GET/POST /addresses`, `GET/PUT/DELETE /addresses/{id}`, `PATCH /addresses/{id}/default` | Authenticated owner |
| Orders | `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | Authenticated owner |
| Payments | `POST/GET /orders/{orderId}/payments`, `GET /payments/{id}`, `POST /payments/{id}/simulate` | Authenticated owner |
| Admin orders | `GET /admin/orders`, `GET /admin/orders/{id}`, `PATCH /admin/orders/{id}/status`, `GET /admin/orders/{id}/payments` | ADMIN |
| Admin inventory | `GET /admin/inventory/{productId}`, `PATCH /admin/inventory/{productId}/stock`, `GET /admin/inventory/low-stock`, `GET /admin/inventory/{productId}/transactions` | ADMIN |

## HTTP semantics

- `201 Created` for resource creation, with `Location` where useful.
- `204 No Content` for logout, delete/deactivate, and cart clear.
- `400 Bad Request` for malformed requests and bean validation.
- `401 Unauthorized` for missing, invalid, or revoked credentials.
- `403 Forbidden` for authenticated users lacking permission or review eligibility.
- `404 Not Found` for missing resources or resources owned by another customer.
- `409 Conflict` for duplicate data, insufficient stock, invalid state transitions, and concurrency conflicts.
- `422 Unprocessable Content` for an empty cart at checkout.
- `429 Too Many Requests` for login/register rate limits.
- `500 Internal Server Error` without stack traces or internals.

Errors share this shape:

```json
{
  "timestamp": "2026-10-27T01:40:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_STOCK",
  "message": "Only 1 unit(s) of Demo Product are available",
  "path": "/api/v1/orders",
  "fieldErrors": null
}
```

Validation errors add `fieldErrors: [{ "field": "quantity", "message": "must be greater than or equal to 1" }]`.

## Examples

### Create an order (idempotent)

`Idempotency-Key` is required. Retrying with the same authenticated customer and key returns the original order without deducting inventory again. The key is scoped by customer.

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 7f240b7a-1a68-4f8a-b0a5-7ab9b1d59a11" \
  -H "Content-Type: application/json" \
  -d '{"addressId":"<address-uuid>"}'
```

```json
{
  "id": "f714...",
  "customerId": "f53e...",
  "status": "PENDING",
  "items": [
    {
      "productId": "cc1b...",
      "productName": "Aurora Noise-Cancelling Headphones",
      "sku": "NX-EL-001",
      "unitPrice": 149.99,
      "quantity": 1,
      "lineTotal": 149.99
    }
  ],
  "subtotal": 149.99,
  "shippingCost": 0.00,
  "discountAmount": 0.00,
  "totalAmount": 149.99,
  "currency": "USD",
  "shippingAddress": {
    "recipientName": "Maya Hassan",
    "line1": "42 Demo Street",
    "line2": "Apartment 7",
    "city": "Cairo",
    "state": "Cairo",
    "postalCode": "11511",
    "country": "Egypt",
    "phone": "+20 100 000 0000"
  },
  "cancelledAt": null,
  "createdAt": "2026-10-27T01:42:00Z",
  "updatedAt": "2026-10-27T01:42:00Z"
}
```

Use a new key for a genuinely new order.

### Initiate a payment

```bash
curl -X POST http://localhost:8080/api/v1/orders/$ORDER_ID/payments \
  -H "Authorization: Bearer $TOKEN"
```

### Complete a simulated payment (development only)

```bash
curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/simulate \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCESS"}'
```

A simulated success moves the order from `PENDING` to `CONFIRMED`. Repeating it is idempotent and does not double-confirm or double-charge anything. The simulator is blocked in `prod`/`production`.

### Admin stock adjustment

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/inventory/$PRODUCT_ID/stock \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delta":-3,"reason":"Damaged units removed during stock count"}'
```

Send either `delta` or `quantity`, not both. A reason is required.

## Postman collection

The full request/response collection is available at [nexacart.postman_collection.json](nexacart.postman_collection.json). Update it when the HTTP contract changes.

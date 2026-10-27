# API Guide

NexaCart exposes a JSON REST API. Business routes are versioned under `/api/v1`; operational endpoints (`/actuator`, `/v3/api-docs`, and `/swagger-ui`) are unversioned. Protected routes require `Authorization: Bearer <token>`.

Related documents: [Architecture](ARCHITECTURE.md) for design, [Configuration and operations](OPERATIONS.md) for runtime settings, [Troubleshooting](TROUBLESHOOTING.md) for failure diagnosis, and the [Postman collection](nexacart.postman_collection.json) for request examples.

## Conventions

### Requests and responses

- Send `Content-Type: application/json` for requests with a body. Responses are JSON.
- IDs are UUIDs. Timestamps are ISO-8601 instants in UTC. Money values are JSON numbers with two decimal places.
- JPA entities are never exposed. Responses use DTO records; collections use a page envelope.
- Unknown JSON properties are ignored. Request DTOs only accept their declared fields, so client-supplied prices, totals, roles, and `verifiedPurchase` values have no effect.

Page responses have this shape:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

All paginated endpoints accept `page` (zero-based, default `0`) and `size` (default `20`, maximum `100`). Out-of-range values are rejected with `400`; request-parameter validation reports `VALIDATION_FAILED`. The admin user list, order lists, review lists, and inventory history are sorted by creation time descending. Product lists accept an explicit `sort` parameter described below.

### Authentication

1. `POST /api/v1/auth/register` creates a `CUSTOMER` account. The email is trimmed and lower-cased. The password must be 10–72 characters and contain at least one letter and one digit. Any attempted `role` or `enabled` value in the payload is ignored.
2. `POST /api/v1/auth/login` returns a short-lived JWT access token. The default lifetime is 900 seconds (`NEXACART_JWT_TTL_SECONDS`).
3. Send `Authorization: Bearer <token>` on protected requests.
4. `POST /api/v1/auth/logout` stores the token's `jti` in `revoked_tokens`. The database insert is idempotent, but after the first logout the token is rejected, so repeating logout with the same token returns `401`.
5. Disabling an account or changing a role takes effect on the next request because the validator and authority converter read current database state.
6. There are no refresh tokens. Log in again after expiry.

The login response includes the token, its expiry, and the safe user profile:

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

### Idempotent checkout

`POST /api/v1/orders` requires an `Idempotency-Key` header (non-blank, maximum 100 characters). The key is trimmed and scoped to the authenticated customer. Retrying with the same customer and key returns the original order with `201` and does **not** deduct inventory again, even if the request body differs. Use a new key for a genuinely new order.

### CORS and rate limiting

CORS only allows origins configured through `NEXACART_CORS_ALLOWED_ORIGINS`. The API allows the methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`; the headers `Authorization`, `Content-Type`, and `Idempotency-Key`; and exposes the `Location` response header. Credentials are not allowed.

Login and registration share a fixed-window rate limit keyed by remote address and request path. The default is 10 requests per 60 seconds per endpoint. A blocked request returns `429` with `RATE_LIMIT_EXCEEDED` and a `Retry-After` header. The limiter is in-memory and per instance; integration tests disable it.

## Endpoint reference

All paths below are relative to `/api/v1`.

### Authentication and users

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `POST /auth/register` | Public | `201` | Body: `email`, `password`, `firstName`, `lastName`. Returns the created profile; log in separately to obtain a token. Duplicate email is `409 EMAIL_ALREADY_REGISTERED`. |
| `POST /auth/login` | Public | `200` | Body: `email`, `password`. Invalid credentials and disabled accounts both return `401 INVALID_CREDENTIALS`. |
| `POST /auth/logout` | Authenticated | `204` | Revokes the presented token's `jti`. |
| `GET /users/me` | Authenticated | `200` | Current profile. |
| `PATCH /users/me` | Authenticated | `200` | Body: `firstName`, `lastName`. |
| `GET /admin/users` | ADMIN | `200` | Optional `query`, `page`, `size`. Searches email, first name, and last name. |
| `GET /admin/users/{userId}` | ADMIN | `200` | Any account. |
| `PATCH /admin/users/{userId}` | ADMIN | `200` | Body: `role` (`ADMIN`/`CUSTOMER`), `enabled`. Self-demotion/disable and removal of the last enabled admin are rejected. |

### Catalog

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `GET /categories` | Public | `200` | Active categories, alphabetical. |
| `GET /categories/{categoryId}` | Public | `200` | Inactive categories return `404`. |
| `GET /admin/categories` | ADMIN | `200` | All categories, alphabetical. |
| `GET /admin/categories/{categoryId}` | ADMIN | `200` | Includes inactive categories. |
| `POST /admin/categories` | ADMIN | `201` | Body: `name`, optional `description`, optional `active`. Name is unique case-insensitively; slug is generated. |
| `PUT /admin/categories/{categoryId}` | ADMIN | `200` | Full update. A name change regenerates the slug. |
| `DELETE /admin/categories/{categoryId}` | ADMIN | `204` | Soft deactivate (`active=false`). |
| `GET /products` | Public | `200` | Only `ACTIVE` products in active categories. Supports filters and sorting below. |
| `GET /products/{productId}` | Public | `200` | `404` when the product or its category is not publicly purchasable. |
| `GET /admin/products` | ADMIN | `200` | All statuses. Optional `search`, `categoryId`, `status`, `page`, `size`, `sort`. |
| `GET /admin/products/{productId}` | ADMIN | `200` | Any status. |
| `POST /admin/products` | ADMIN | `201` | Body: `categoryId`, `name`, `sku`, optional `description`, `price`, `initialStock`, optional `lowStockThreshold`. |
| `PUT /admin/products/{productId}` | ADMIN | `200` | Full update; does not change stock or the existing slug. |
| `DELETE /admin/products/{productId}` | ADMIN | `204` | Soft deactivate (`INACTIVE`). |

`GET /products` query parameters:

| Parameter | Meaning |
|---|---|
| `search` | Case-insensitive contains match on product name, description, and SKU. |
| `categoryId` | Restrict to a category. |
| `minPrice`, `maxPrice` | Inclusive price bounds. `minPrice` greater than `maxPrice` returns `400 INVALID_PRICE_RANGE`. |
| `available` | `true` for stock greater than zero; `false` for zero or missing inventory. |
| `page`, `size` | Pagination as described above. |
| `sort` | `<field>,<asc|desc>`. Allowed fields: `name`, `price`, `createdAt`, `updatedAt`, `sku`. Default: `createdAt,desc`. Invalid values return `400 INVALID_SORT`. |

Product creation normalizes the SKU to upper case, generates a unique slug, rounds the price to scale 2, sets status to `ACTIVE`, and initializes inventory. `lowStockThreshold` defaults to 5. Product updates do not modify inventory; use the admin inventory endpoint. Products can only be created or updated with an active category.

### Reviews

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `GET /products/{productId}/reviews` | Public | `200` | Returns `averageRating`, `reviewCount`, and a page of reviews. Public product required. |
| `POST /products/{productId}/reviews` | Authenticated | `201` | Requires a `DELIVERED` order containing the product. Body: `rating` (1–5), optional `title` (max 150), optional `comment` (max 2000). |
| `PATCH /reviews/{reviewId}` | Authenticated owner | `200` | Same body as create. Other customers receive `404`. |
| `DELETE /reviews/{reviewId}` | Authenticated owner | `204` | Other customers receive `404`. |

One review per customer per product is enforced (`409 REVIEW_ALREADY_EXISTS`). `verifiedPurchase` is always true for reviews created through the API. Ineligible customers receive `403 REVIEW_NOT_ELIGIBLE`, not `404`.

### Cart

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `GET /cart` | Authenticated | `200` | Returns the cart, creating an empty one on first access. |
| `POST /cart/items` | Authenticated | `200` | Body: `productId`, `quantity` (1–1000). Adds to an existing line. |
| `PATCH /cart/items/{productId}` | Authenticated | `200` | Body: `quantity` (1–1000). |
| `DELETE /cart/items/{productId}` | Authenticated | `200` | Returns the updated cart. |
| `DELETE /cart` | Authenticated | `204` | Clears all items. |

The cart is server-side and always priced from current product prices. Adding or updating checks that the product is purchasable and that the requested quantity is within stock; the total quantity for one product cannot exceed 1000. `checkoutReady` is true only when the cart is non-empty and every line is available with sufficient stock. Cart totals are estimates; checkout recalculates them and stores snapshots.

### Addresses

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `GET /addresses` | Authenticated | `200` | Default address first, then newest. |
| `GET /addresses/{addressId}` | Authenticated owner | `200` | Other customers receive `404`. |
| `POST /addresses` | Authenticated | `201` | Body: `recipientName`, `line1`, optional `line2`, `city`, optional `state`, optional `postalCode`, `country`, optional `phone`, optional `defaultAddress`. |
| `PUT /addresses/{addressId}` | Authenticated owner | `200` | Full update; `defaultAddress` optional. |
| `PATCH /addresses/{addressId}/default` | Authenticated owner | `200` | Makes this the single default address. |
| `DELETE /addresses/{addressId}` | Authenticated owner | `204` | If another address remains, one is promoted to default. |

At least one default address is guaranteed whenever the customer has addresses. The first address becomes the default even when `defaultAddress` is omitted. Setting a new default clears the previous one. If the only default is explicitly unset, the service promotes an address again.

### Orders

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `POST /orders` | Authenticated | `201` | Requires `Idempotency-Key`; body: `addressId`. Creates a `PENDING` order from the current cart. |
| `GET /orders` | Authenticated | `200` | Optional `status`, `page`, `size`. Newest first. |
| `GET /orders/{orderId}` | Authenticated owner | `200` | Other customers receive `404`. |
| `POST /orders/{orderId}/cancel` | Authenticated owner | `200` | `PENDING` and `CONFIRMED` only. Repeated cancellation returns the existing cancelled order. |
| `GET /admin/orders` | ADMIN | `200` | Optional `status`, `userId`, `page`, `size`. Newest first. |
| `GET /admin/orders/{orderId}` | ADMIN | `200` | Any order. |
| `PATCH /admin/orders/{orderId}/status` | ADMIN | `200` | Body: `status`. Validated transitions only. |
| `GET /admin/orders/{orderId}/payments` | ADMIN | `200` | Payment history for any order. |

Checkout reserves stock immediately. The order stores immutable product, price, and shipping-address snapshots. The cart must be non-empty (`422 CART_EMPTY`), the address must belong to the customer (`404 ADDRESS_NOT_FOUND`), and every product must be purchasable with sufficient stock (`409 PRODUCT_UNAVAILABLE` or `409 INSUFFICIENT_STOCK`). All mutations occur in one transaction.

Status transitions:

```text
PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
   |           |            |
   +-----------+------------+--> CANCELLED
```

- Customer cancellation: `PENDING` and `CONFIRMED`.
- Admin cancellation: `PENDING`, `CONFIRMED`, and `PROCESSING`.
- No cancellation after `SHIPPED`.
- Invalid transitions return `409 INVALID_ORDER_STATUS_TRANSITION`; setting the same non-cancelled status returns `409 ORDER_STATUS_UNCHANGED`.
- Admins may confirm an unpaid order deliberately; payment success also confirms a `PENDING` order.
- Cancelling a paid order refunds the successful payment and restores inventory exactly once.

### Payments

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `POST /orders/{orderId}/payments` | Authenticated owner | `201` | Requires the payment simulator. Only `PENDING` orders. Returns an existing `PENDING` payment if one exists. |
| `GET /orders/{orderId}/payments` | Authenticated owner | `200` | All payment attempts for the order, newest first. |
| `GET /payments/{paymentId}` | Authenticated owner | `200` | Other customers receive `404`. |
| `POST /payments/{paymentId}/simulate` | Authenticated owner | `200` | Requires the payment simulator. Body: `outcome` = `SUCCESS` or `FAILURE`. |

Payment initiation and simulation both return `503 PAYMENT_PROVIDER_UNAVAILABLE` when no provider is enabled. The development simulator is enabled by the `dev` profile and blocked in `prod`/`production`; reading payment records is not gated. A simulated `SUCCESS` moves a `PENDING` order to `CONFIRMED`. Repeating the same terminal outcome is idempotent; changing a terminal outcome returns `409 PAYMENT_ALREADY_FINALIZED`. A failed payment can be retried by initiating a new payment for the same `PENDING` order. Only one `PENDING` payment per order is allowed.

### Admin inventory

| Method and path | Access | Success | Notes |
|---|---|---|---|
| `GET /admin/inventory/low-stock` | ADMIN | `200` | Inventory where `quantity <= lowStockThreshold`. |
| `GET /admin/inventory/{productId}` | ADMIN | `200` | Current balance and threshold. |
| `PATCH /admin/inventory/{productId}/stock` | ADMIN | `200` | Body: exactly one of `delta` or `quantity`, optional `lowStockThreshold`, required `reason`. |
| `GET /admin/inventory/{productId}/transactions` | ADMIN | `200` | Optional `page`, `size`. Newest first. |

For stock adjustment, send exactly one of:

- `delta`: a non-zero signed change.
- `quantity`: the new absolute balance (zero or greater).

A zero delta, both fields, or neither field returns `400 INVALID_STOCK_ADJUSTMENT`; an adjustment that changes nothing returns `400 NO_STOCK_CHANGE`; reducing below zero returns `409 NEGATIVE_STOCK`. A stock change writes an `ADMIN_ADJUSTMENT` ledger row. A threshold-only change does not write a ledger row.

## HTTP semantics

- `201 Created` for resource creation, including idempotent order and payment-initiation replies. `Location` is set where useful.
- `204 No Content` for logout, resource deletion/deactivation (addresses, reviews, categories, products), and cart clear.
- Removing a single cart item returns `200` with the updated cart, not `204`.
- `400 Bad Request` for malformed requests, bean validation, invalid pagination/sorting, invalid stock adjustments, and missing required headers.
- `401 Unauthorized` for missing, invalid, expired, or revoked credentials.
- `403 Forbidden` for authenticated users lacking permission or review eligibility.
- `404 Not Found` for missing resources, resources owned by another customer, and non-public catalog resources.
- `405 Method Not Allowed` for unsupported HTTP methods.
- `409 Conflict` for duplicate data, insufficient stock, invalid state transitions, terminal payment conflicts, and concurrency conflicts.
- `415 Unsupported Media Type` for a non-JSON content type on a body-bearing request.
- `422 Unprocessable Content` for an empty cart at checkout.
- `429 Too Many Requests` for login/register rate limits.
- `500 Internal Server Error` without stack traces or internals.
- `503 Service Unavailable` when a payment operation requires a provider that is not enabled.

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

Validation errors add `fieldErrors`:

```json
{
  "timestamp": "2026-10-27T01:40:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/orders",
  "fieldErrors": [
    { "field": "addressId", "message": "must not be null" }
  ]
}
```

The rate limiter returns the same envelope with `code: "RATE_LIMIT_EXCEEDED"` and a `Retry-After` header.

### Common error codes

| Code | Typical status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation or method-parameter validation failed. |
| `MALFORMED_REQUEST` | 400 | Missing header/parameter, invalid JSON, or type mismatch. |
| `INVALID_CREDENTIALS` | 401 | Login failed; the reason is intentionally generic. |
| `UNAUTHORIZED` | 401 | No valid authenticated principal. |
| `ACCESS_DENIED` | 403 | Authenticated but not allowed to perform the operation. |
| `REVIEW_NOT_ELIGIBLE` | 403 | No delivered purchase of the product. |
| `*_NOT_FOUND` (e.g. `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`) | 404 | Resource is missing or owned by another customer. |
| `EMAIL_ALREADY_REGISTERED` | 409 | Duplicate account email. |
| `SKU_EXISTS`, `CATEGORY_NAME_EXISTS` | 409 | Duplicate catalog identifiers. |
| `INSUFFICIENT_STOCK` | 409 | Requested quantity exceeds available stock. |
| `PRODUCT_UNAVAILABLE` | 409 | Product or its category is inactive. |
| `QUANTITY_LIMIT_EXCEEDED` | 400 | More than 1000 units of one product in the cart. |
| `CART_EMPTY` | 422 | Checkout attempted with no cart items. |
| `ADDRESS_NOT_FOUND` | 404 | Address does not exist for this customer. |
| `INVALID_ORDER_STATUS_TRANSITION` | 409 | Transition is not allowed by the state machine. |
| `ORDER_NOT_CANCELLABLE` | 409 | Order has already shipped/delivered. |
| `ORDER_NOT_PAYABLE` | 409 | Payment initiation on a non-`PENDING` order. |
| `PAYMENT_ALREADY_FINALIZED` | 409 | Attempt to change a terminal payment state. |
| `PAYMENT_PROVIDER_UNAVAILABLE` | 503 | Simulator/provider is disabled. |
| `INVALID_STOCK_ADJUSTMENT` | 400 | Both/neither adjustment fields or an invalid value. |
| `NO_STOCK_CHANGE` | 400 | Adjustment would not change stock or threshold. |
| `NEGATIVE_STOCK` | 409 | Adjustment would reduce stock below zero. |
| `REVIEW_ALREADY_EXISTS` | 409 | One review per customer per product. |
| `SELF_ADMIN_CHANGE_NOT_ALLOWED` | 400 | Admin tried to demote or disable themselves. |
| `LAST_ADMIN` | 409 | Operation would remove the last enabled administrator. |
| `DATA_CONFLICT` | 409 | A database uniqueness/invariant constraint was violated. |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic or pessimistic lock conflict. |
| `RATE_LIMIT_EXCEEDED` | 429 | Login/register limit exceeded. |
| `INTERNAL_ERROR` | 500 | Unhandled server error; no internals are returned. |

## Examples

### Log in

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@nexacart.local","password":"Customer123!"}'
```

### Register a customer

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"new.customer@example.com","password":"Password123!","firstName":"New","lastName":"Customer"}'
```

### Add a cart item

```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productId":"<product-uuid>","quantity":2}'
```

### Create an order (idempotent)

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

### Admin stock adjustment

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/inventory/$PRODUCT_ID/stock \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delta":-3,"reason":"Damaged units removed during stock count"}'
```

### Create a review (delivered purchase required)

```bash
curl -X POST http://localhost:8080/api/v1/products/$PRODUCT_ID/reviews \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"rating":5,"title":"Excellent","comment":"Exactly as described"}'
```

## Postman collection

[nexacart.postman_collection.json](nexacart.postman_collection.json) is a representative end-to-end collection covering the main customer and admin flows. It is not an exhaustive endpoint list; use the endpoint reference above or Swagger UI for the complete contract. Update the collection when the HTTP contract changes.

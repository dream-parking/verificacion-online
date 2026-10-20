# Customer number (VDI-122, story VDI-27; covers VDI-26)

Every customer has a unique, permanent **customer number** such as `CLI-0000001`.

## When it is assigned

When the customer's **first request is submitted** (`POST /api/onboarding/requests/{id}/submit`), whatever status the
request ends in (`COMPLETED`, `ENHANCED_REVIEW` or `AWAITING_RNPN`). A customer that already has a number keeps it with
every later request. Until the first submission the number is `null`.

## Why it never repeats

- The number comes from the database sequence `customer_number_seq`, so two submissions at the same time can never get
  the same one. A sequence can leave gaps (a submission that is rolled back uses a number) but never repeats one.
- `customer.number` has a unique constraint and the format `CLI-` plus at least seven digits.
- A trigger refuses any change or removal of a number once assigned, so not even SQL can rewrite or reuse it.
- Two submissions of the **same** customer at the same time: the customer's row is locked before the number is read, so
  the second one waits and finds the number the first one assigned. Both end with the same number.
- The entity maps the column as read-only; only `CustomerRepository.assignNumber` writes it, and only where it is still
  null. Correcting the customer's data therefore can never overwrite a number assigned in the meantime.

Migration `V30__customer_number.sql` also gives a number to the customers that had already submitted a request, in the
order of their first submission, and the sequence continues after the last one.

## Where it is delivered

| Where | Field |
|---|---|
| `GET /api/console/customers/{id}` and `GET /api/console/customers?dui=` | `number` |
| `GET /api/console/requests/{id}` | `customerNumber` |
| `GET /api/console/customers/risk-history?number=CLI-0000001` | see below |

## Risk signals by customer number

`GET /api/console/customers/risk-history?number=CLI-0000001` returns, for each request of the customer (the oldest
first): its `status`, every score assessment (`assessments`, the oldest first, the last one is the one in force) and the
decisions taken on submit (`decisions`, VDI-117). Nothing is stored for it: the requests already point to the customer.
`404` when no customer has that number, `400` when the number does not have the format.

The mobile app does not receive the customer number.

## Not done here

- Linking or closing VDI-26 with VDI-27 in Jira is a decision of the PO (noted in the ticket).
- The console screen that shows the number is VDI-123.

// Customer number (VDI-122). Contract: docs/customer-number.md, GET /customers/risk-history?number=.

import { ApiError, apiFetch } from "./api";

/** CLI- plus at least seven digits, the format the API accepts. */
const CUSTOMER_NUMBER = /^CLI-\d{7,}$/;

/** The customer number written in a search box, in upper case, or null when it is not a complete one. */
export function customerNumberOf(text: string): string | null {
  const value = text.trim().toUpperCase();
  return CUSTOMER_NUMBER.test(value) ? value : null;
}

/** The person started writing a customer number but it is not complete yet (for example "cli-00"). */
export const isPartialCustomerNumber = (text: string) => /^\s*cli/i.test(text) && !customerNumberOf(text);

type RiskHistory = { customerNumber: string; requests: { requestId: string }[] };

/**
 * Ids of every request of the customer with that number. The request list of the API does not carry the customer
 * number, so the console asks for the customer's history. Null when no customer has that number (404).
 */
export async function fetchCustomerRequestIds(token: string, number: string): Promise<string[] | null> {
  try {
    const history = await apiFetch<RiskHistory>(
      `/api/console/customers/risk-history?number=${encodeURIComponent(number)}`,
      { token },
    );
    return history.requests.map((r) => r.requestId);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

#!/usr/bin/env bash
#
# Search for flights using the SerpApi Google Flights API.
#
# Required environment variables:
#   SERPAPI_KEY          - Your SerpApi API key
#
# Optional environment variables (with defaults):
#   DEPARTURE_ID        - Departure airport IATA code (default: JFK)
#   ARRIVAL_ID          - Arrival airport IATA code (default: CDG)
#   OUTBOUND_DATE       - Departure date YYYY-MM-DD (default: 7 days from now)
#   RETURN_DATE         - Return date YYYY-MM-DD (default: 14 days from now)
#   CURRENCY            - Currency code (default: USD)
#   LANGUAGE            - Language code (default: en)
#   TRAVEL_CLASS        - 1=Economy, 2=Premium Economy, 3=Business, 4=First (default: 1)
#   ADULTS              - Number of adults (default: 1)
#   OUTPUT_FILE         - File to save JSON response (optional, prints to stdout if unset)
#

set -euo pipefail

# ---- Validate required env vars ----
if [[ -z "${SERPAPI_KEY:-}" ]]; then
  echo "ERROR: SERPAPI_KEY environment variable is required." >&2
  echo "  export SERPAPI_KEY='your-api-key'" >&2
  exit 1
fi

# ---- Defaults ----
DEPARTURE_ID="${DEPARTURE_ID:-JFK}"
ARRIVAL_ID="${ARRIVAL_ID:-CDG}"
OUTBOUND_DATE="${OUTBOUND_DATE:-$(date -d '+7 days' +%Y-%m-%d 2>/dev/null || date -v+7d +%Y-%m-%d)}"
RETURN_DATE="${RETURN_DATE:-$(date -d '+14 days' +%Y-%m-%d 2>/dev/null || date -v+14d +%Y-%m-%d)}"
CURRENCY="${CURRENCY:-USD}"
LANGUAGE="${LANGUAGE:-en}"
TRAVEL_CLASS="${TRAVEL_CLASS:-1}"
ADULTS="${ADULTS:-1}"

# ---- Build request URL ----
BASE_URL="https://serpapi.com/search"
PARAMS="engine=google_flights"
PARAMS+="&departure_id=${DEPARTURE_ID}"
PARAMS+="&arrival_id=${ARRIVAL_ID}"
PARAMS+="&outbound_date=${OUTBOUND_DATE}"
PARAMS+="&return_date=${RETURN_DATE}"
PARAMS+="&currency=${CURRENCY}"
PARAMS+="&hl=${LANGUAGE}"
PARAMS+="&travel_class=${TRAVEL_CLASS}"
PARAMS+="&adults=${ADULTS}"
PARAMS+="&api_key=${SERPAPI_KEY}"

URL="${BASE_URL}?${PARAMS}"

echo "Searching flights: ${DEPARTURE_ID} → ${ARRIVAL_ID}"
echo "  Outbound: ${OUTBOUND_DATE}"
echo "  Return:   ${RETURN_DATE}"
echo "  Currency: ${CURRENCY}"
echo "  Class:    ${TRAVEL_CLASS}"
echo ""

# ---- Make the API call ----
RESPONSE=$(curl -s -w "\n%{http_code}" "${URL}")
HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [[ "${HTTP_CODE}" -ne 200 ]]; then
  echo "ERROR: API returned HTTP ${HTTP_CODE}" >&2
  echo "${BODY}" >&2
  exit 1
fi

# ---- Output ----
if [[ -n "${OUTPUT_FILE:-}" ]]; then
  echo "${BODY}" > "${OUTPUT_FILE}"
  echo "Response saved to ${OUTPUT_FILE}"
else
  echo "${BODY}"
fi

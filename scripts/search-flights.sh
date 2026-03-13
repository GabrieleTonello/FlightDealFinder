#!/usr/bin/env bash
#
# Search for flights using the SerpApi Google Flights API.
#
# Required environment variable:
#   SERPAPI_KEY  - Your SerpApi API key
#
# Usage:
#   ./scripts/search-flights.sh <departure> <arrival> <outbound_date> <return_date> [output_file]
#
# Example:
#   ./scripts/search-flights.sh JFK CDG 2025-08-01 2025-08-15
#   ./scripts/search-flights.sh LAX NRT 2025-09-01 2025-09-10 results.json
#

set -euo pipefail

# ---- Fixed constants ----
CURRENCY="EUR"
LANGUAGE="en"
TRAVEL_CLASS=1
ADULTS=1

# ---- Validate API key ----
if [[ -z "${SERPAPI_KEY:-}" ]]; then
  echo "ERROR: SERPAPI_KEY environment variable is required." >&2
  echo "  export SERPAPI_KEY='your-api-key'" >&2
  exit 1
fi

# ---- Validate arguments ----
if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <departure> <arrival> <outbound_date> <return_date> [output_file]" >&2
  echo "  departure     - IATA airport code (e.g. JFK, LAX, CDG)" >&2
  echo "  arrival       - IATA airport code (e.g. NRT, FCO, BCN)" >&2
  echo "  outbound_date - Departure date YYYY-MM-DD" >&2
  echo "  return_date   - Return date YYYY-MM-DD" >&2
  echo "  output_file   - Optional file to save JSON response" >&2
  exit 1
fi

DEPARTURE_ID="$1"
ARRIVAL_ID="$2"
OUTBOUND_DATE="$3"
RETURN_DATE="$4"
OUTPUT_FILE="${5:-}"

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
if [[ -n "${OUTPUT_FILE}" ]]; then
  echo "${BODY}" > "${OUTPUT_FILE}"
  echo "Response saved to ${OUTPUT_FILE}"
else
  echo "${BODY}"
fi

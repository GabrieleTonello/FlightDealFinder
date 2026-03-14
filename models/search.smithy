$version: "2"

namespace com.flightdeal.model

@documentation("Error details for a failed route search against the SerpApi Google Flights API")
structure SearchError {
    @required
    @documentation("The route that failed (e.g. 'JFK-CDG')")
    destination: String

    @required
    errorMessage: String

    @required
    @documentation("Error category: HTTP_ERROR, TIMEOUT, IO_ERROR, PARSE_ERROR")
    errorType: String
}

list SearchErrorList {
    member: SearchError
}

@documentation("Result of the flight search operation across all configured routes")
structure FlightSearchResult {
    @required
    @documentation("Best flights as identified by Google Flights (may be empty)")
    bestFlights: FlightDealList

    @required
    @documentation("Other available flights")
    otherFlights: FlightDealList

    @required
    @documentation("Errors encountered during route searches")
    errors: SearchErrorList
}

@documentation("The message published to the SNS Deal Topic after a search completes")
structure DealBatchMessage {
    @required
    bestFlights: FlightDealList

    @required
    otherFlights: FlightDealList

    @required
    @documentation("ISO-8601 timestamp of when the search was performed")
    searchTimestamp: String

    @required
    @documentation("Departure airport IATA code")
    departureId: String

    @required
    @documentation("Arrival airport IATA code")
    arrivalId: String
}

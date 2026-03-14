$version: "2"

namespace com.flightdeal.model

@documentation("A record stored in the DynamoDB FlightPriceHistory table for historical price tracking. Partition key is the route (e.g. 'JFK-CDG'), sort key is the retrieval timestamp.")
structure PriceRecord {
    @required
    @documentation("Route as 'DEPARTURE-ARRIVAL' (e.g. 'JFK-CDG') — DynamoDB partition key")
    destination: String

    @required
    @documentation("ISO-8601 timestamp of when this record was stored — DynamoDB sort key")
    timestamp: String

    @required
    @documentation("Ticket price in EUR")
    price: Integer

    @required
    @documentation("Departure airport IATA code")
    departureAirport: String

    @required
    @documentation("Arrival airport IATA code")
    arrivalAirport: String

    @required
    @documentation("Departure time from the first flight segment")
    departureTime: String

    @required
    @documentation("Arrival time from the last flight segment")
    arrivalTime: String

    @required
    airline: String

    @required
    @documentation("Total trip duration in minutes")
    totalDuration: Integer

    flightNumber: String

    @required
    @documentation("ISO-8601 timestamp of when the deal was retrieved from the API")
    retrievalTimestamp: String
}

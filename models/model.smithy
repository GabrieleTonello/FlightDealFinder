$version: "2"

namespace com.flightdeal.model

/// An airport with name, IATA code, and time
structure Airport {
    @required
    name: String

    @required
    id: String

    time: String
}

/// A single flight segment within a trip
structure FlightSegment {
    @required
    departureAirport: Airport

    @required
    arrivalAirport: Airport

    @required
    duration: Integer

    airplane: String

    @required
    airline: String

    airlineLogo: String

    travelClass: String

    flightNumber: String

    legroom: String

    overnight: Boolean

    oftenDelayed: Boolean
}

list FlightSegmentList {
    member: FlightSegment
}

/// A layover between flight segments
structure Layover {
    @required
    duration: Integer

    @required
    name: String

    @required
    id: String

    overnight: Boolean
}

list LayoverList {
    member: Layover
}

/// Carbon emissions data for a flight
structure CarbonEmissions {
    thisFlight: Integer

    typicalForRoute: Integer

    differencePercent: Integer
}

/// A complete flight deal (one or more segments + layovers + price)
structure FlightDeal {
    @required
    flights: FlightSegmentList

    layovers: LayoverList

    @required
    totalDuration: Integer

    carbonEmissions: CarbonEmissions

    @required
    price: Integer

    flightType: String

    airlineLogo: String

    departureToken: String

    bookingToken: String
}

list FlightDealList {
    member: FlightDeal
}

/// A time window representing a free period in the user's calendar
structure TimeWindow {
    @required
    startDate: String

    @required
    endDate: String
}

list TimeWindowList {
    member: TimeWindow
}

/// A date range for calendar queries
structure DateRange {
    @required
    startDate: String

    @required
    endDate: String
}

/// A record stored in DynamoDB for historical price tracking
structure PriceRecord {
    @required
    destination: String

    @required
    timestamp: String

    @required
    price: Integer

    @required
    departureAirport: String

    @required
    arrivalAirport: String

    @required
    departureTime: String

    @required
    arrivalTime: String

    @required
    airline: String

    @required
    totalDuration: Integer

    flightNumber: String

    @required
    retrievalTimestamp: String
}

/// The message published to SNS Deal Topic
structure DealBatchMessage {
    @required
    bestFlights: FlightDealList

    @required
    otherFlights: FlightDealList

    @required
    searchTimestamp: String

    @required
    departureId: String

    @required
    arrivalId: String
}

/// Error details for a failed destination search
structure SearchError {
    @required
    destination: String

    @required
    errorMessage: String

    @required
    errorType: String
}

list SearchErrorList {
    member: SearchError
}

/// Result of the flight search operation
structure FlightSearchResult {
    @required
    bestFlights: FlightDealList

    @required
    otherFlights: FlightDealList

    @required
    errors: SearchErrorList
}

/// Input to the Calendar Service step
structure CalendarLookupInput {
    @required
    dateRange: DateRange

    @required
    deals: FlightDealList
}

/// Output from the Calendar Service step
structure CalendarLookupOutput {
    @required
    freeWindows: TimeWindowList
}

/// Input to the Flight Matcher step
structure MatchInput {
    @required
    freeWindows: TimeWindowList

    @required
    deals: FlightDealList
}

/// Output from the Flight Matcher step
structure MatchOutput {
    @required
    matchedDeals: FlightDealList
}

/// Input to the Notification Service step
structure NotificationInput {
    @required
    matchedDeals: FlightDealList

    @required
    recipientEmail: String
}

/// Output from the Notification Service step
structure NotificationOutput {
    @required
    success: Boolean

    @required
    messageId: String
}

/// Workflow start result
structure WorkflowStartResult {
    @required
    executionArn: String
}

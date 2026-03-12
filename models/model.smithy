$version: "2"

namespace com.flightdeal.model

/// A single flight deal from the external API
structure FlightDeal {
    @required
    destination: String

    @required
    price: BigDecimal

    @required
    departureDate: String

    @required
    returnDate: String

    @required
    airline: String
}

/// A time window representing a free period in the user's calendar
structure TimeWindow {
    @required
    startDate: String

    @required
    endDate: String
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
    price: BigDecimal

    @required
    departureDate: String

    @required
    returnDate: String

    @required
    airline: String

    @required
    retrievalTimestamp: String
}

/// The message published to SNS Deal Topic
structure DealBatchMessage {
    @required
    deals: FlightDealList

    @required
    searchTimestamp: String

    @required
    destinationsSearched: Integer
}

list FlightDealList {
    member: FlightDeal
}

list TimeWindowList {
    member: TimeWindow
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
    deals: FlightDealList

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

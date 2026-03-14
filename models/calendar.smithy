$version: "2"

namespace com.flightdeal.model

@documentation("A time window representing a free period in the user's Google Calendar")
structure TimeWindow {
    @required
    @documentation("Start date of the free window (ISO-8601 date, e.g. '2025-07-01')")
    startDate: String

    @required
    @documentation("End date of the free window (ISO-8601 date, e.g. '2025-07-15')")
    endDate: String
}

list TimeWindowList {
    member: TimeWindow
}

@documentation("A date range for querying the Google Calendar API")
structure DateRange {
    @required
    startDate: String

    @required
    endDate: String
}

@documentation("Input to the Calendar Service step in the matching workflow")
structure CalendarLookupInput {
    @required
    dateRange: DateRange

    @required
    deals: FlightDealList
}

@documentation("Output from the Calendar Service step containing free time windows")
structure CalendarLookupOutput {
    @required
    freeWindows: TimeWindowList
}

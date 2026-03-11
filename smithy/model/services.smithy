$version: "2"

namespace com.flightdeal.service

use com.flightdeal.model#FlightSearchResult
use com.flightdeal.model#CalendarLookupInput
use com.flightdeal.model#CalendarLookupOutput
use com.flightdeal.model#MatchInput
use com.flightdeal.model#MatchOutput
use com.flightdeal.model#NotificationInput
use com.flightdeal.model#NotificationOutput

service FlightSearchService {
    version: "2025-01-01"
    operations: [SearchFlights]
}

@readonly
operation SearchFlights {
    output: FlightSearchResult
}

service CalendarService {
    version: "2025-01-01"
    operations: [LookupCalendar]
}

@readonly
operation LookupCalendar {
    input: CalendarLookupInput
    output: CalendarLookupOutput
}

service FlightMatcherService {
    version: "2025-01-01"
    operations: [MatchFlights]
}

@readonly
operation MatchFlights {
    input: MatchInput
    output: MatchOutput
}

service NotificationService {
    version: "2025-01-01"
    operations: [SendNotification]
}

operation SendNotification {
    input: NotificationInput
    output: NotificationOutput
}

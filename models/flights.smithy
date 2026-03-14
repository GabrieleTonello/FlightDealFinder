$version: "2"

namespace com.flightdeal.model

@documentation("An airport with IATA code, full name, and optional departure/arrival time")
structure Airport {
    @required
    @documentation("Full airport name (e.g. 'John F. Kennedy International Airport')")
    name: String

    @required
    @documentation("IATA airport code (e.g. 'JFK', 'CDG')")
    id: String

    @documentation("Departure or arrival time in format 'YYYY-MM-DD HH:mm'")
    time: String
}

@documentation("A single flight segment within a trip, representing one takeoff-to-landing leg")
structure FlightSegment {
    @required
    departureAirport: Airport

    @required
    arrivalAirport: Airport

    @required
    @documentation("Flight duration in minutes")
    duration: Integer

    @documentation("Aircraft model (e.g. 'Boeing 777', 'Airbus A350')")
    airplane: String

    @required
    @documentation("Airline name (e.g. 'Air France', 'American')")
    airline: String

    @documentation("URL to the airline logo image")
    airlineLogo: String

    @documentation("Travel class (e.g. 'Economy', 'Business')")
    travelClass: String

    @documentation("Flight number (e.g. 'AF 11', 'AA 42')")
    flightNumber: String

    @documentation("Legroom including unit (e.g. '31 in')")
    legroom: String

    @documentation("True if the flight is overnight")
    overnight: Boolean

    @documentation("True if the flight is often delayed by 30+ minutes")
    oftenDelayed: Boolean
}

list FlightSegmentList {
    member: FlightSegment
}

@documentation("A layover between flight segments at a connecting airport")
structure Layover {
    @required
    @documentation("Layover duration in minutes")
    duration: Integer

    @required
    @documentation("Name of the layover airport")
    name: String

    @required
    @documentation("IATA code of the layover airport")
    id: String

    @documentation("True if the layover is overnight")
    overnight: Boolean
}

list LayoverList {
    member: Layover
}

@documentation("Carbon emissions data for a flight, in grams")
structure CarbonEmissions {
    @documentation("Carbon emissions of this specific flight in grams")
    thisFlight: Integer

    @documentation("Typical carbon emissions for this route in grams")
    typicalForRoute: Integer

    @documentation("Percentage difference from typical emissions")
    differencePercent: Integer
}

@documentation("A complete flight deal from the SerpApi Google Flights API response. Contains one or more segments, optional layovers, total duration, price, and carbon data.")
structure FlightDeal {
    @required
    @documentation("List of flight segments (legs) in this trip")
    flights: FlightSegmentList

    @documentation("Layovers between segments, empty for direct flights")
    layovers: LayoverList

    @required
    @documentation("Total trip duration in minutes including layovers")
    totalDuration: Integer

    carbonEmissions: CarbonEmissions

    @required
    @documentation("Ticket price in the requested currency (EUR)")
    price: Integer

    @documentation("Flight type, e.g. 'Round trip'")
    flightType: String

    @documentation("URL to the airline logo for mixed-airline flights")
    airlineLogo: String

    @documentation("Token for retrieving return flights")
    departureToken: String

    @documentation("Token for retrieving booking options")
    bookingToken: String
}

list FlightDealList {
    member: FlightDeal
}

package com.flightdeal.proxy;

import com.flightdeal.generated.model.Airport;
import com.flightdeal.generated.model.CarbonEmissions;
import com.flightdeal.generated.model.FlightDeal;
import com.flightdeal.generated.model.FlightSegment;
import com.flightdeal.generated.model.Layover;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps SerpApi GSON {@link JsonObject} responses (snake_case) to Smithy-generated types
 * (camelCase). This is the single boundary where raw JSON is converted to typed models.
 */
@Slf4j
public final class SerpApiResponseMapper {

  private SerpApiResponseMapper() {}

  /** Maps a top-level flight deal JSON object to a {@link FlightDeal}. */
  public static FlightDeal mapFlightDeal(JsonObject json) {
    List<FlightSegment> segments = new ArrayList<>();
    if (json.has("flights") && json.get("flights").isJsonArray()) {
      JsonArray arr = json.getAsJsonArray("flights");
      for (int i = 0; i < arr.size(); i++) {
        segments.add(mapFlightSegment(arr.get(i).getAsJsonObject()));
      }
    }

    List<Layover> layovers = null;
    if (json.has("layovers") && json.get("layovers").isJsonArray()) {
      layovers = new ArrayList<>();
      JsonArray arr = json.getAsJsonArray("layovers");
      for (int i = 0; i < arr.size(); i++) {
        layovers.add(mapLayover(arr.get(i).getAsJsonObject()));
      }
    }

    CarbonEmissions carbon = null;
    if (json.has("carbon_emissions") && json.get("carbon_emissions").isJsonObject()) {
      carbon = mapCarbonEmissions(json.getAsJsonObject("carbon_emissions"));
    }

    var builder =
        FlightDeal.builder()
            .flights(segments)
            .totalDuration(getInt(json, "total_duration"))
            .price(getInt(json, "price"));

    if (layovers != null) {
      builder.layovers(layovers);
    }
    if (carbon != null) {
      builder.carbonEmissions(carbon);
    }
    if (json.has("type")) {
      builder.flightType(getString(json, "type"));
    }
    if (json.has("airline_logo")) {
      builder.airlineLogo(getString(json, "airline_logo"));
    }
    if (json.has("departure_token")) {
      builder.departureToken(getString(json, "departure_token"));
    }
    if (json.has("booking_token")) {
      builder.bookingToken(getString(json, "booking_token"));
    }

    return builder.build();
  }

  /** Maps a single flight segment JSON object to a {@link FlightSegment}. */
  static FlightSegment mapFlightSegment(JsonObject json) {
    var builder =
        FlightSegment.builder()
            .departureAirport(
                json.has("departure_airport")
                    ? mapAirport(json.getAsJsonObject("departure_airport"))
                    : Airport.builder().name("").id("").build())
            .arrivalAirport(
                json.has("arrival_airport")
                    ? mapAirport(json.getAsJsonObject("arrival_airport"))
                    : Airport.builder().name("").id("").build())
            .duration(getInt(json, "duration"))
            .airline(getString(json, "airline") != null ? getString(json, "airline") : "");

    if (json.has("airplane")) {
      builder.airplane(getString(json, "airplane"));
    }
    if (json.has("airline_logo")) {
      builder.airlineLogo(getString(json, "airline_logo"));
    }
    if (json.has("travel_class")) {
      builder.travelClass(getString(json, "travel_class"));
    }
    if (json.has("flight_number")) {
      builder.flightNumber(getString(json, "flight_number"));
    }
    if (json.has("legroom")) {
      builder.legroom(getString(json, "legroom"));
    }
    if (json.has("overnight")) {
      builder.overnight(json.get("overnight").getAsBoolean());
    }
    if (json.has("often_delayed")) {
      builder.oftenDelayed(json.get("often_delayed").getAsBoolean());
    }

    return builder.build();
  }

  /** Maps an airport JSON object to an {@link Airport}. */
  static Airport mapAirport(JsonObject json) {
    var builder =
        Airport.builder()
            .name(getString(json, "name") != null ? getString(json, "name") : "")
            .id(getString(json, "id") != null ? getString(json, "id") : "");

    if (json.has("time")) {
      builder.time(getString(json, "time"));
    }

    return builder.build();
  }

  /** Maps a layover JSON object to a {@link Layover}. */
  static Layover mapLayover(JsonObject json) {
    var builder =
        Layover.builder()
            .duration(getInt(json, "duration"))
            .name(getString(json, "name") != null ? getString(json, "name") : "")
            .id(getString(json, "id") != null ? getString(json, "id") : "");

    if (json.has("overnight")) {
      builder.overnight(json.get("overnight").getAsBoolean());
    }

    return builder.build();
  }

  /** Maps a carbon emissions JSON object to a {@link CarbonEmissions}. */
  static CarbonEmissions mapCarbonEmissions(JsonObject json) {
    var builder = CarbonEmissions.builder();

    if (json.has("this_flight")) {
      builder.thisFlight(json.get("this_flight").getAsInt());
    }
    if (json.has("typical_for_route")) {
      builder.typicalForRoute(json.get("typical_for_route").getAsInt());
    }
    if (json.has("difference_percent")) {
      builder.differencePercent(json.get("difference_percent").getAsInt());
    }

    return builder.build();
  }

  private static int getInt(JsonObject obj, String key) {
    return obj.has(key) ? obj.get(key).getAsInt() : 0;
  }

  private static String getString(JsonObject obj, String key) {
    return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
  }
}

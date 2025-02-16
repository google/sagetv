package sage.epg.sd;

public enum SDErrors
{
  OK(0 /*, "OK"*/),
  INVALID_JSON(1001 /*, "Unable to decode JSON."*/),
  DEFLATE_REQUIRED(1002 /*, "Did not receive Accept-Encoding: deflate in request."*/),
  TOKEN_MISSING(1004 /*, "Token required but not provided in request header."*/),
  UNSUPPORTED_COMMAND(2000 /*, "Unsupported command."*/),
  REQUIRED_ACTION_MISSING(2001 /*, "Request is missing an action to take."*/),
  REQUIRED_REQUEST_MISSING(2002 /*, "Did not receive request."*/),
  REQUIRED_PARAMETER_MISSING_COUNTRY(2004 /*, "In order to search for lineups, you must supply a 3-letter country parameter."*/),
  REQUIRED_PARAMETER_MISSING_POSTALCODE(2005 /*, "In order to search for lineups, you must supply a postal code parameter."*/),
  REQUIRED_PARAMETER_MISSING_MSGID(2006 /*, "In order to delete a message you must supply the messageID."*/),
  INVALID_PARAMETER_COUNTRY(2050 /*, "The COUNTRY parameter must be ISO-3166-1 alpha 3. See http://en.wikipedia.org/wiki/ISO_3166-1_alpha-3."*/),
  INVALID_PARAMETER_POSTALCODE(2051 /*, "The POSTALCODE parameter must be valid for the country you are searching. Post message to http://forums.schedulesdirect.org/viewforum.php?f=6 if you are having issues."*/),
  INVALID_PARAMETER_FETCHTYPE(2052 /*, "You provided a fetch type I don't know how to handle."*/),
  INVALID_PARAMETER_DEBUG(2055 /*, "Unexpected debug connection from client."*/),
  DUPLICATE_LINEUP(2100 /*, "Lineup already in account."*/),
  LINEUP_NOT_FOUND(2101 /*, "Lineup not in account. Add lineup to account before requesting mapping."*/),
  UNKNOWN_LINEUP(2102 /*, "Invalid lineup requested. Check your COUNTRY / POSTALCODE combination for validity."*/),
  INVALID_LINEUP_DELETE(2103 /*, "Delete of lineup not in account."*/),
  LINEUP_WRONG_FORMAT(2104 /*, "Lineup must be formatted COUNTRY-LINEUP-DEVICE or COUNTRY-OTA-POSTALCODE"*/),
  INVALID_LINEUP(2105 /*, "The lineup you submitted doesn't exist."*/),
  LINEUP_DELETED(2106 /*, "The lineup you requested has been deleted from the server."*/),
  LINEUP_QUEUED(2107 /*, "The lineup is being generated on the server. Please retry."*/),
  INVALID_COUNTRY(2108 /*, "The country you requested is either mis-typed or does not have valid data."*/),
  STATIONID_NOT_FOUND(2200 /*, "The stationID you requested is not in any of your lineups."*/),
  SERVICE_OFFLINE(3000 /*, "Server offline for maintenance."*/),
  ACCOUNT_EXPIRED(4001 /*, "Account expired."*/),
  INVALID_HASH(4002 /*, "Password hash must be lowercase 40 character sha1_hex of password."*/),
  INVALID_USER(4003 /*, "Invalid username or password."*/),
  ACCOUNT_LOCKOUT(4004 /*, "Too many login failures. Locked for 15 minutes."*/),
  ACCOUNT_DISABLED(4005 /*, "Account has been disabled. Please contact Schedules Direct support: admin@schedulesdirect.org for more information."*/),
  TOKEN_EXPIRED(4006 /*, "Token has expired. Request new token."*/),
  MAX_LINEUP_CHANGES_REACHED(4100 /*, "Exceeded maximum number of lineup changes for today."*/),
  MAX_LINEUPS(4101 /*, "Exceeded number of lineups for this account."*/),
  NO_LINEUPS(4102 /*, "No lineups have been added to this account."*/),
  IMAGE_NOT_FOUND(5000 /*, "Could not find requested image. Post message to http://forums.schedulesdirect.org/viewforum.php?f=6 if you are having issues."*/),
  INVALID_PROGRAMID(6000 /*, "Could not find requested programID. Permanent failure."*/),
  PROGRAMID_QUEUED(6001 /*, "ProgramID should exist at the server, but doesn't. The server will regenerate the JSON for the program, so your application should retry."*/),
  FUTURE_PROGRAM(6002 /*, "The programID you requested has not occurred yet, so isComplete status is unknown."*/),
  SCHEDULE_NOT_FOUND(7000 /*, "The schedule you requested should be available. Post message to http://forums.schedulesdirect.org/viewforum.php?f=6"*/),
  INVALID_SCHEDULE_REQUEST(7010 /*, "The server can't determine whether your schedule is valid or not. Open a support ticket."*/),
  SCHEDULE_RANGE_EXCEEDED(7020 /*, "The date that you've requested is outside of the range of the data for that stationID."*/),
  SCHEDULE_NOT_IN_LINEUP(7030 /*, "You have requested a schedule which is not in any of your configured lineups."*/),
  SCHEDULE_QUEUED(7100 /*, "The schedule you requested has been queued for generation but is not yet ready for download. Retry."*/),
  HCF(9999 /*, "Unknown error. Open support ticket."*/),
  SAGETV_COMMUNICATION_ERROR(-1000 /*, "Unable to communicate with the Schedules Direct server."*/),
  SAGETV_TOKEN_RETURN_MISSING(-2000 /*, "Schedules Direct did not return a valid token."*/),
  SAGETV_SERVICE_MISSING(-2001 /*, "The requested service is not currently available from Schedules Direct."*/),
  SAGETV_NO_PASSWORD(-2002 /*, "A username and password have not been provided to connect to Schedules Direct."*/),
  SAGETV_UNKNOWN(-9999 /*, "Unknown error to SageTV."*/);

  public final int CODE;

  SDErrors(int code)
  {
    CODE = code;
  }

  /**
   * Gets the associated error enumeration for a provided code.
   *
   * @param code The code to look up.
   * @return The associated enumeration for the provided code. This will never return
   *         <code>null</code>.
   */
  public static SDErrors getErrorForCode(int code)
  {
    for (SDErrors error : SDErrors.values())
    {
      if (code == error.CODE)
        return error;
    }

    return SDErrors.SAGETV_UNKNOWN;
  }

  /**
   * Gets the associated error enumeration for a provided name.
   *
   * @param name The code to look up.
   * @return The associated enumeration for the provided name. This will never return
   *         <code>null</code>.
   */
  public static SDErrors getErrorForName(String name)
  {
    for (SDErrors error : SDErrors.values())
    {
      if (name.equals(error.name()))
        return error;
    }

    return SDErrors.SAGETV_UNKNOWN;
  }

  /**
   * Throws an associated error for a provided code.
   *
   * @param code The code to look up and throw.
   * @throws SDException Always thrown by this method.
   */
  public static void throwErrorForCode(int code) throws SDException
  {
    // We don't know why this is an error when the code is 0, but this method was called, so we will
    // throw an error.
    if (code != 0)
    {
      for (SDErrors error : SDErrors.values())
      {
        if (code == error.CODE)
          throw new SDException(error);
      }
    }

    throw new SDException(SDErrors.SAGETV_UNKNOWN);
  }
}

/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sage;

public class TVTuningFrequencies
{
  private TVTuningFrequencies(){}

  public static final int NTSC_M = 0x00000001;
  public static final int NTSC_M_J = 0x00000002;
  public static final int NTSC_433 = 0x00000004;
  public static final int PAL_B = 0x00000010;
  public static final int PAL_D = 0x00000020;
  public static final int PAL_H = 0x00000080;
  public static final int PAL_I = 0x00000100;
  public static final int PAL_M = 0x00000200;
  public static final int PAL_N = 0x00000400;
  public static final int PAL_60 = 0x00000800;
  public static final int SECAM_B = 0x00001000;
  public static final int SECAM_D = 0x00002000;
  public static final int SECAM_G = 0x00004000;
  public static final int SECAM_H = 0x00008000;
  public static final int SECAM_K = 0x00010000;
  public static final int SECAM_K1 = 0x00020000;
  public static final int SECAM_L = 0x00040000;
  public static final int SECAM_L1 = 0x00080000;

  public static final String[] COUNTRIES = {
    "United States of America",
    "Anguilla",
    "Antigua",
    "Bahamas",
    "Barbados",
    "Bermuda",
    "British Virgin Islands",
    "Canada",
    "Cayman Islands",
    "Dominica",
    "Dominican Republic",
    "Grenada",
    "Jamaica",
    "Montserrat",
    "Nevis",
    "St. Kitts",
    "St. Vincent and the Grenadines",
    "Trinidad and Tobago",
    "Turks and Caicos Islands",
    "Barbuda",
    "Puerto Rico",
    "Saint Lucia",
    "United States Virgin Islands",
    //		"Canada", // duplicate
    "Russia",
    "Kazakhstan",
    "Kyrgyzstan",
    "Tajikistan",
    "Turkmenistan",
    "Uzbekistan",
    "Egypt",
    "South Africa",
    "Greece",
    "Netherlands",
    "Belgium",
    "France",
    "Spain",
    "Hungary",
    "Italy",
    "Vatican City",
    "Romania",
    "Switzerland",
    "Liechtenstein",
    "Austria",
    "United Kingdom",
    "Denmark",
    "Sweden",
    "Norway",
    "Poland",
    "Germany",
    "Peru",
    "Mexico",
    "Cuba",
    "Guantanamo Bay",
    "Argentina",
    "Brazil",
    "Chile",
    "Colombia",
    "Bolivarian Republic of Venezuela",
    "Malaysia",
    "Australia",
    "Cocos-Keeling Islands",
    "Indonesia",
    "Philippines",
    "New Zealand",
    "Singapore",
    "Thailand",
    "Japan",
    "Korea (South)",
    "Vietnam",
    "China",
    "Turkey",
    "India",
    "Pakistan",
    "Afghanistan",
    "Sri Lanka",
    "Myanmar",
    "Iran",
    "Morocco",
    "Algeria",
    "Tunisia",
    "Libya",
    "Gambia",
    "Senegal Republic",
    "Mauritania",
    "Mali",
    "Guinea",
    "Cote D'Ivoire",
    "Burkina Faso",
    "Niger",
    "Togo",
    "Benin",
    "Mauritius",
    "Liberia",
    "Sierra Leone",
    "Ghana",
    "Nigeria",
    "Chad",
    "Central African Republic",
    "Cameroon",
    "Cape Verde Islands",
    "Sao Tome and Principe",
    "Equatorial Guinea",
    "Gabon",
    "Congo",
    "Congo(DRC)",
    "Angola",
    "Guinea-Bissau",
    "Diego Garcia",
    "Ascension Island",
    "Seychelle Islands",
    "Sudan",
    "Rwanda",
    "Ethiopia",
    "Somalia",
    "Djibouti",
    "Kenya",
    "Tanzania",
    "Uganda",
    "Burundi",
    "Mozambique",
    "Zambia",
    "Madagascar",
    "Reunion Island",
    "Zimbabwe",
    "Namibia",
    "Malawi",
    "Lesotho",
    "Botswana",
    "Swaziland",
    "Mayotte Island",
    "Comoros",
    "St. Helena",
    "Eritrea",
    "Aruba",
    "Faroe Islands",
    "Greenland",
    "Gibraltar",
    "Portugal",
    "Luxembourg",
    "Ireland",
    "Iceland",
    "Albania",
    "Malta",
    "Cyprus",
    "Finland",
    "Bulgaria",
    "Lithuania",
    "Latvia",
    "Estonia",
    "Moldova",
    "Armenia",
    "Belarus",
    "Andorra",
    "Monaco",
    "San Marino",
    "Ukraine",
    "Serbia and Montenegro",
    "Croatia",
    "Slovenia",
    "Bosnia and Herzegovina",
    "F.Y.R.O.M. (Former Yugoslav Republic of Macedonia)",
    "Czech Republic",
    "Slovak Republic",
    "Falkland Islands (Islas Malvinas)",
    "Belize",
    "Guatemala",
    "El Salvador",
    "Honduras",
    "Nicaragua",
    "Costa Rica",
    "Panama",
    "St. Pierre and Miquelon",
    "Haiti",
    "Guadeloupe",
    "French Antilles",
    "Bolivia",
    "Guyana",
    "Ecuador",
    "French Guiana",
    "Paraguay",
    "Martinique",
    "Suriname",
    "Uruguay",
    "Netherlands Antilles",
    "Saipan Island",
    "Rota Island",
    "Tinian Island",
    "Guam",
    "Christmas Island",
    "Australian Antarctic Territory",
    "Norfolk Island",
    "Brunei",
    "Nauru",
    "Papua New Guinea",
    "Tonga",
    "Solomon Islands",
    "Vanuatu",
    "Fiji Islands",
    "Palau",
    "Wallis and Futuna Islands",
    "Cook Islands",
    "Niue",
    "Territory of American Samoa",
    "Samoa",
    "Kiribati Republic",
    "New Caledonia",
    "Tuvalu",
    "French Polynesia",
    "Tokelau",
    "Micronesia",
    "Marshall Islands",
    "Korea (North)",
    "Hong Kong SAR",
    "Macao SAR",
    "Cambodia",
    "Laos",
    "INMARSAT (Atlantic-East)",
    "INMARSAT (Pacific)",
    "INMARSAT (Indian)",
    "INMARSAT (Atlantic-West)",
    "Bangladesh",
    "Taiwan",
    "Maldives",
    "Lebanon",
    "Jordan",
    "Syria",
    "Iraq",
    "Kuwait",
    "Saudi Arabia",
    "Yemen",
    "Oman",
    "United Arab Emirates",
    "Israel",
    "Bahrain",
    "Qatar",
    "Bhutan",
    "Mongolia",
    "Nepal",
    "Azerbaijan",
    "Georgia"
  };
  public static final String[] ISO_COUNTRY_CODES = {
    "US", //"United States of America",
    "AI", //"Anguilla",
    "AG", //"Antigua",
    "BS", //"Bahamas",
    "BB", //"Barbados",
    "BM", //"Bermuda",
    "VG", //"British Virgin Islands",
    "CA", //"Canada",
    "KY", //"Cayman Islands",
    "DM", //"Dominica",
    "DO", //"Dominican Republic",
    "GD", //"Grenada",
    "JM", //"Jamaica",
    "MS", //"Montserrat",
    "KN", //"Nevis",
    "KN", //"St. Kitts",
    "VC", //"St. Vincent and the Grenadines",
    "TT", //"Trinidad and Tobago",
    "TC", //"Turks and Caicos Islands",
    "AG", //"Barbuda",
    "PR", //"Puerto Rico",
    "LC", //"Saint Lucia",
    "UM", //"United States Virgin Islands",
    //		"Canada", // duplicate
    "RU", //"Russia",
    "KZ", //"Kazakhstan",
    "KG", //"Kyrgyzstan",
    "TJ", //"Tajikistan",
    "TM", //"Turkmenistan",
    "UZ", //"Uzbekistan",
    "EG", //"Egypt",
    "ZA", //"South Africa",
    "GR", //"Greece",
    "NL", //"Netherlands",
    "BE", //"Belgium",
    "FR", //"France",
    "ES", //"Spain",
    "HU", //"Hungary",
    "IT", //"Italy",
    "VA", //"Vatican City",
    "RO", //"Romania",
    "CH", //"Switzerland",
    "LI", //"Liechtenstein",
    "AT", //"Austria",
    "GB", //"United Kingdom",
    "DK", //"Denmark",
    "SE", //"Sweden",
    "NO", //"Norway",
    "PL", //"Poland",
    "DE", //"Germany",
    "PE", //"Peru",
    "MX", //"Mexico",
    "CU", //"Cuba",
    "CU", //"Guantanamo Bay",
    "AR", //"Argentina",
    "BR", //"Brazil",
    "CL", //"Chile",
    "CO", //"Colombia",
    "BO", //"Bolivarian Republic of Venezuela",
    "MY", //"Malaysia",
    "AU", //"Australia",
    "CC", //"Cocos-Keeling Islands",
    "ID", //"Indonesia",
    "PH", //"Philippines",
    "NZ", //"New Zealand",
    "SG", //"Singapore",
    "TH", //"Thailand",
    "JP", //"Japan",
    "KR", //"Korea (South)",
    "VN", //"Vietnam",
    "CN", //"China",
    "TR", //"Turkey",
    "IN", //"India",
    "PK", //"Pakistan",
    "AF", //"Afghanistan",
    "LK", //"Sri Lanka",
    "MM", //"Myanmar",
    "IR", //"Iran",
    "MA", //"Morocco",
    "DZ", //"Algeria",
    "TN", //"Tunisia",
    "LY", //"Libya",
    "GM", //"Gambia",
    "SN", //"Senegal Republic",
    "MR", //"Mauritania",
    "ML", //"Mali",
    "GN", //"Guinea",
    "CI", //"Cote D'Ivoire",
    "BF", //"Burkina Faso",
    "NE", //"Niger",
    "TG", //"Togo",
    "BJ", //"Benin",
    "MU", //"Mauritius",
    "LR", //"Liberia",
    "SL", //"Sierra Leone",
    "GH", //"Ghana",
    "NG", //"Nigeria",
    "TD", //"Chad",
    "CF", //"Central African Republic",
    "CM", //"Cameroon",
    "CV", //"Cape Verde Islands",
    "ST", //"Sao Tome and Principe",
    "GQ", //"Equatorial Guinea",
    "GA", //"Gabon",
    "CG", //"Congo",
    "CD", //"Congo(DRC)",
    "AO", //"Angola",
    "GW", //"Guinea-Bissau",
    "", //"Diego Garcia",
    "", //"Ascension Island",
    "SC", //"Seychelle Islands",
    "SD", //"Sudan",
    "RW", //"Rwanda",
    "ET", //"Ethiopia",
    "SO", //"Somalia",
    "DJ", //"Djibouti",
    "KE", //"Kenya",
    "TZ", //"Tanzania",
    "UG", //"Uganda",
    "BI", //"Burundi",
    "MZ", //"Mozambique",
    "ZM", //"Zambia",
    "MG", //"Madagascar",
    "RE", //"Reunion Island",
    "ZW", //"Zimbabwe",
    "NA", //"Namibia",
    "MW", //"Malawi",
    "LS", //"Lesotho",
    "BW", //"Botswana",
    "SZ", //"Swaziland",
    "YT", //"Mayotte Island",
    "KM", //"Comoros",
    "SH", //"St. Helena",
    "ER", //"Eritrea",
    "AW", //"Aruba",
    "FO", //"Faroe Islands",
    "GL", //"Greenland",
    "GI", //"Gibraltar",
    "PT", //"Portugal",
    "LU", //"Luxembourg",
    "IE", //"Ireland",
    "IS", //"Iceland",
    "AL", //"Albania",
    "MT", //"Malta",
    "CY", //"Cyprus",
    "FI", //"Finland",
    "BG", //"Bulgaria",
    "LT", //"Lithuania",
    "LV", //"Latvia",
    "EE", //"Estonia",
    "MD", //"Moldova",
    "AM", //"Armenia",
    "BY", //"Belarus",
    "AD", //"Andorra",
    "MC", //"Monaco",
    "SM", //"San Marino",
    "UA", //"Ukraine",
    "CS", //"Serbia and Montenegro",
    "HR", //"Croatia",
    "SI", //"Slovenia",
    "BA", //"Bosnia and Herzegovina",
    "MK", //"F.Y.R.O.M. (Former Yugoslav Republic of Macedonia)",
    "CZ", //"Czech Republic",
    "SK", //"Slovak Republic",
    "FK", //"Falkland Islands (Islas Malvinas)",
    "BZ", //"Belize",
    "GT", //"Guatemala",
    "SV", //"El Salvador",
    "HN", //"Honduras",
    "NI", //"Nicaragua",
    "CR", //"Costa Rica",
    "PA", //"Panama",
    "PM", //"St. Pierre and Miquelon",
    "HT", //"Haiti",
    "GP", //"Guadeloupe",
    "FR", //"French Antilles",
    "BO", //"Bolivia",
    "GY", //"Guyana",
    "EC", //"Ecuador",
    "GF", //"French Guiana",
    "PY", //"Paraguay",
    "MQ", //"Martinique",
    "SR", //"Suriname",
    "UY", //"Uruguay",
    "AN", //"Netherlands Antilles",
    "", //"Saipan Island",
    "", //"Rota Island",
    "", //"Tinian Island",
    "GU", //"Guam",
    "CK", //"Christmas Island",
    "AQ", //"Australian Antarctic Territory",
    "NF", //"Norfolk Island",
    "BN", //"Brunei",
    "NR", //"Nauru",
    "PG", //"Papua New Guinea",
    "TO", //"Tonga",
    "SB", //"Solomon Islands",
    "VU", //"Vanuatu",
    "FJ", //"Fiji Islands",
    "PW", //"Palau",
    "WF", //"Wallis and Futuna Islands",
    "CK", //"Cook Islands",
    "NU", //"Niue",
    "AS", //"Territory of American Samoa",
    "WS", //"Samoa",
    "KI", //"Kiribati Republic",
    "NC", //"New Caledonia",
    "TV", //"Tuvalu",
    "PF", //"French Polynesia",
    "TK", //"Tokelau",
    "FM", //"Micronesia",
    "MH", //"Marshall Islands",
    "KP", //"Korea (North)",
    "HK", //"Hong Kong SAR",
    "MO", //"Macao SAR",
    "KH", //"Cambodia",
    "LA", //"Laos",
    "", //"INMARSAT (Atlantic-East)",
    "", //"INMARSAT (Pacific)",
    "", //"INMARSAT (Indian)",
    "", //"INMARSAT (Atlantic-West)",
    "BD", //"Bangladesh",
    "TW", //"Taiwan",
    "MV", //"Maldives",
    "LB", //"Lebanon",
    "JO", //"Jordan",
    "SY", //"Syria",
    "IQ", //"Iraq",
    "KW", //"Kuwait",
    "SA", //"Saudi Arabia",
    "YE", //"Yemen",
    "OM", //"Oman",
    "AE", //"United Arab Emirates",
    "IL", //"Israel",
    "BH", //"Bahrain",
    "QA", //"Qatar",
    "BT", //"Bhutan",
    "MN", //"Mongolia",
    "NP", //"Nepal",
    "AZ", //"Azerbaijan",
    "GE", //"Georgia"
  };
  public static final int[] COUNTRY_CODES = {
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    1,
    //		2,
    7,
    7,
    7,
    7,
    7,
    7,
    20,
    27,
    30,
    31,
    32,
    33,
    34,
    36,
    39,
    39,
    40,
    41,
    41,
    43,
    44,
    45,
    46,
    47,
    48,
    49,
    51,
    52,
    53,
    53,
    54,
    55,
    56,
    57,
    58,
    60,
    61,
    61,
    62,
    63,
    64,
    65,
    66,
    81,
    82,
    84,
    86,
    90,
    91,
    92,
    93,
    94,
    95,
    98,
    212,
    213,
    216,
    218,
    220,
    221,
    222,
    223,
    224,
    225,
    226,
    227,
    228,
    229,
    230,
    231,
    232,
    233,
    234,
    235,
    236,
    237,
    238,
    239,
    240,
    241,
    242,
    243,
    244,
    245,
    246,
    247,
    248,
    249,
    250,
    251,
    252,
    253,
    254,
    255,
    256,
    257,
    258,
    260,
    261,
    262,
    263,
    264,
    265,
    266,
    267,
    268,
    269,
    269,
    290,
    291,
    297,
    298,
    299,
    350,
    351,
    352,
    353,
    354,
    355,
    356,
    357,
    358,
    359,
    370,
    371,
    372,
    373,
    374,
    375,
    376,
    377,
    378,
    380,
    381,
    385,
    386,
    387,
    389,
    420,
    421,
    500,
    501,
    502,
    503,
    504,
    505,
    506,
    507,
    508,
    509,
    590,
    590,
    591,
    592,
    593,
    594,
    595,
    596,
    597,
    598,
    599,
    670,
    670,
    670,
    671,
    672,
    672,
    672,
    673,
    674,
    675,
    676,
    677,
    678,
    679,
    680,
    681,
    682,
    683,
    684,
    685,
    686,
    687,
    688,
    689,
    690,
    691,
    692,
    850,
    852,
    853,
    855,
    856,
    871,
    872,
    873,
    874,
    880,
    886,
    960,
    961,
    962,
    963,
    964,
    965,
    966,
    967,
    968,
    971,
    972,
    973,
    974,
    975,
    976,
    977,
    994,
    995,
  };
  public static final int F_FIX_BROAD = 0;
  public static final int F_USA_BROAD = 1;
  public static final int F_USA_CABLE = 2;
  public static final int F_OZ__BROAD = 3;
  public static final int F_CHN_BROAD = 4;
  public static final int F_CHN_CABLE = 5;
  public static final int F_CZE_BROAD = 6;
  public static final int F_EEU_BROAD = 7;
  public static final int F_FRA_BROAD = 8;
  public static final int F_FOT_BROAD = 9;
  public static final int F_IRE_BROAD = 10;
  public static final int F_ITA_BROAD = 11;
  public static final int F_JAP_BROAD = 12;
  public static final int F_JAP_CABLE = 13;
  public static final int F_NZ__BROAD = 14;
  public static final int F_UK__BROAD = 15;
  public static final int F_UK__CABLE = 16;
  public static final int F_WEU_BROAD = 17;
  public static final int F_WEU_CABLE = 18;
  public static final int F_UNI_CABLE = 19;

  public static final int[][] CHANNEL_MIN_MAX = {
    { 2, 69 }, // FIX_BROAD
    { 2, 69 }, // USA_BROAD
    { 1, 158 }, // USA_CABLE
    { 1, 53 }, // OZ__BROAD
    { 1, 68 }, // CHN_BROAD
    { 1, 110 }, // CHN_CABLE
    { 1, 69 }, // CZE_BROAD
    { 1, 69 }, // EEU_BROAD
    { 2, 69 }, // FRA_BROAD
    { 4, 9 }, // FOT_BROAD
    { 1, 10 }, // IRE_BROAD
    { 1, 10 }, // ITA_BROAD
    { 1, 62 }, // JAP_BROAD
    { 1, 113 }, // JAP_CABLE
    { 1, 11 }, // NZ__BROAD
    { 1, 69 }, // UK__BROAD
    { 1, 107 }, // UK__CABLE
    { 1, 69 }, // WEU_BROAD
    { 1, 107 }, // WEU_CABLE
    { 1, 368 } // UNI_CABLE
  };

  public static final int[] CABLE_AIR_FREQ_CODES = {
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    //		F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_UK__BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_WEU_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FRA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_ITA_BROAD,
    F_UNI_CABLE, F_ITA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UK__CABLE, F_UK__BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_WEU_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_WEU_CABLE, F_WEU_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_OZ__BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_NZ__BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_JAP_CABLE, F_JAP_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_CHN_CABLE, F_CHN_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_FIX_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_IRE_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_ITA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_ITA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_CZE_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_UK__BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_FOT_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_UK__BROAD,
    F_UNI_CABLE, F_UK__BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_USA_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_WEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_USA_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
    F_UNI_CABLE, F_EEU_BROAD,
  };

  public static final int[] VIDEO_FORMAT_CODES = {
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    //NTSC_M,
    SECAM_D,
    SECAM_D,
    SECAM_D,
    SECAM_D,
    SECAM_D,
    SECAM_D,
    SECAM_B,
    PAL_I,
    SECAM_B,
    PAL_B,
    PAL_B,
    SECAM_L,
    PAL_B,
    PAL_D,
    PAL_B,
    PAL_B,
    PAL_D,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_I,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_N,
    PAL_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_B,
    PAL_B,
    NTSC_M,
    PAL_B,
    NTSC_M,
    PAL_B,
    PAL_B,
    PAL_B,
    NTSC_M_J,
    NTSC_M,
    NTSC_M,
    PAL_D,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    NTSC_M,
    SECAM_B,
    SECAM_B,
    PAL_B,
    SECAM_B,
    SECAM_B,
    SECAM_K,
    SECAM_K,
    SECAM_B,
    SECAM_K,
    SECAM_K,
    SECAM_K,
    SECAM_K,
    SECAM_K,
    SECAM_K,
    SECAM_K,
    SECAM_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    NTSC_M,
    PAL_B,
    SECAM_B,
    SECAM_K,
    SECAM_D,
    SECAM_K,
    PAL_I,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    SECAM_K,
    PAL_B,
    PAL_B,
    PAL_B,
    SECAM_K,
    PAL_B,
    PAL_B,
    SECAM_K,
    SECAM_K,
    PAL_B,
    PAL_I,
    NTSC_M,
    PAL_I,
    SECAM_K,
    PAL_B,
    SECAM_K,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_B,
    NTSC_M,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_I,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    SECAM_D,
    PAL_B,
    SECAM_D,
    PAL_B,
    SECAM_D,
    SECAM_D,
    SECAM_D,
    NTSC_M,
    SECAM_G,
    PAL_B,
    SECAM_D,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_D,
    PAL_B,
    PAL_I,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    SECAM_K,
    NTSC_M,
    SECAM_K,
    NTSC_M,
    PAL_N,
    SECAM_K,
    NTSC_M,
    SECAM_K,
    PAL_N,
    SECAM_K,
    NTSC_M,
    PAL_N,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_B,
    PAL_B,
    NTSC_M,
    PAL_B,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    SECAM_K,
    PAL_B,
    NTSC_M,
    NTSC_M,
    PAL_B,
    PAL_B,
    SECAM_K,
    NTSC_M,
    SECAM_K,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    SECAM_D,
    PAL_I,
    PAL_I,
    PAL_B,
    PAL_B,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    NTSC_M,
    PAL_B,
    NTSC_M,
    PAL_B,
    SECAM_B,
    PAL_B,
    SECAM_B,
    SECAM_B,
    PAL_B,
    SECAM_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    PAL_B,
    NTSC_M,
    SECAM_D,
    PAL_B,
    SECAM_D,
    SECAM_D
  };

  public static final int getCountryCode(String country)
  {
    for (int i = 0; i < COUNTRIES.length; i++)
      if (COUNTRIES[i].equalsIgnoreCase(country))
        return COUNTRY_CODES[i];
    return 0;
  }
  public static final String getISOCountryCode(String country)
  {
    for (int i = 0; i < COUNTRIES.length; i++)
      if (COUNTRIES[i].equalsIgnoreCase(country))
        return ISO_COUNTRY_CODES[i];
    return "";
  }
  private static int getCCIndex(int countryCode)
  {
    int rv = java.util.Arrays.binarySearch(COUNTRY_CODES, countryCode);
    rv = Math.max(0, Math.min(COUNTRY_CODES.length - 1, rv));
    return rv;
  }
  public static final int getCableChannelMax(int countryCode)
  {
    int idx = getCCIndex(countryCode);
    return CHANNEL_MIN_MAX[CABLE_AIR_FREQ_CODES[idx*2]][1];
  }
  public static final int getCableChannelMin(int countryCode)
  {
    int idx = getCCIndex(countryCode);
    return CHANNEL_MIN_MAX[CABLE_AIR_FREQ_CODES[idx*2]][0];
  }
  public static final int getBroadcastChannelMax(int countryCode)
  {
    int idx = getCCIndex(countryCode);
    return CHANNEL_MIN_MAX[CABLE_AIR_FREQ_CODES[idx*2 + 1]][1];
  }
  public static final int getBroadcastChannelMin(int countryCode)
  {
    int idx = getCCIndex(countryCode);
    return CHANNEL_MIN_MAX[CABLE_AIR_FREQ_CODES[idx*2 + 1]][0];
  }
  public static final int getVideoFormatCode(int countryCode)
  {
    int idx = getCCIndex(countryCode);
    return VIDEO_FORMAT_CODES[idx];
  }

  private static String[][] DVBT_REGIONS = new String[COUNTRIES.length][];
  private static String[][] DVBC_REGIONS = new String[COUNTRIES.length][];
  private static String[] DVBS_REGIONS = Pooler.EMPTY_STRING_ARRAY;

  private static boolean loadedTuningFreqFiles = false;
  private static final Object tuningFileLock = new Object();
  private static java.util.Map buildCountryRegionMap(String sourceFile)
  {
    String[] sortedCountries = (String[])COUNTRIES.clone();
    java.util.Arrays.sort(sortedCountries);
    java.io.BufferedReader br = null;
    java.util.Map countryToRegionsMap = new java.util.HashMap();
    try
    {
      br = new java.io.BufferedReader(new java.io.FileReader(sourceFile));
      while (true)
      {
        String currLine = br.readLine();
        if (currLine == null)
          break;
        currLine = currLine.trim();
        // Skip empty lines and comments
        if (currLine.length() == 0 || currLine.charAt(0) == '#')
          continue;
        while (currLine.charAt(0) == '"')
        {
          // Line starts with a quote, that's what we want. Find the closing quote. There may be multiple on one line separated by commas
          int nextQuote = currLine.indexOf('"', 1);
          if (nextQuote == -1)
            break;
          String currName = currLine.substring(1, nextQuote).trim();
          //if (Sage.DBG) System.out.println("Found country/region name of: " + currName);

          // Find it in our sorted list
          int nameIndex = java.util.Arrays.binarySearch(sortedCountries, currName);
          if (nameIndex >= 0)
          {
            //System.out.println(currName + " is a country");
          }
          else
          {
            // Find the matching country name.
            nameIndex = -1 * (nameIndex + 1);
            do
            {
              nameIndex--; // to align with the country name we'd be inserted after
            }while (nameIndex >= 0 && !currName.startsWith(sortedCountries[nameIndex]));
            if (nameIndex >= 0)
            {
              if (currName.startsWith(sortedCountries[nameIndex]))
              {
                String currCountry = sortedCountries[nameIndex];
                String currRegion = currName.substring(sortedCountries[nameIndex].length() + 1);
                //if (Sage.DBG) System.out.println(currRegion + " is a region in country " + currCountry);
                java.util.Vector regionList = (java.util.Vector) countryToRegionsMap.get(currCountry);
                if (regionList == null)
                {
                  regionList = new java.util.Vector();
                  countryToRegionsMap.put(currCountry, regionList);
                }
                regionList.add(currRegion);
              }
              //								else
              //									if (Sage.DBG) System.out.println("ERROR in country name of:" + currName + " it did not match:" + sortedCountries[nameIndex]);
            }
            //							else
            //								if (Sage.DBG) System.out.println("ERROR in country name of:" + currName);
          }

          // Find the next quote if it's there
          nextQuote = currLine.indexOf('"', nextQuote + 1);
          if (nextQuote != -1)
            currLine = currLine.substring(nextQuote);
          else
            break;
        }
      }
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR: Could not load " + sourceFile + " file!!!!!");
    }
    finally
    {
      try
      {
        if (br != null)
          br.close();
        br = null;
      }
      catch (Exception e){}
    }
    return countryToRegionsMap;
  }
  private static String[] buildRegionList(String sourceFile)
  {
    java.io.BufferedReader br = null;
    java.util.Vector regionsList = new java.util.Vector();
    try
    {
      br = new java.io.BufferedReader(new java.io.FileReader(sourceFile));
      while (true)
      {
        String currLine = br.readLine();
        if (currLine == null)
          break;
        currLine = currLine.trim();
        // Skip empty lines and comments
        if (currLine.length() == 0 || currLine.charAt(0) == '#')
          continue;
        while (currLine.charAt(0) == '"')
        {
          // Line starts with a quote, that's what we want. Find the closing quote. There may be multiple on one line separated by commas
          int nextQuote = currLine.indexOf('"', 1);
          if (nextQuote == -1)
            break;
          String currName = currLine.substring(1, nextQuote).trim();
          regionsList.add(currName);
          // Find the next quote if it's there
          nextQuote = currLine.indexOf('"', nextQuote + 1);
          if (nextQuote != -1)
            currLine = currLine.substring(nextQuote);
          else
            break;
        }
      }
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR: Could not load " + sourceFile + " file!!!!!");
    }
    finally
    {
      try
      {
        if (br != null)
          br.close();
        br = null;
      }
      catch (Exception e){}
    }
    return (String[]) regionsList.toArray(Pooler.EMPTY_STRING_ARRAY);
  }
  private static void loadTuningFreqFile()
  {
    if (loadedTuningFreqFiles)
      return;
    synchronized (tuningFileLock)
    {
      if (loadedTuningFreqFiles)
        return;

      java.util.Map countryToRegionsMap = buildCountryRegionMap("PredefinedDVBT.frq");
      for (int i = 0; i < COUNTRIES.length; i++)
      {
        java.util.Vector currList = (java.util.Vector) countryToRegionsMap.get(COUNTRIES[i]);
        if (currList != null)
        {
          DVBT_REGIONS[i] = (String[]) currList.toArray(Pooler.EMPTY_STRING_ARRAY);
        }
      }
      countryToRegionsMap = buildCountryRegionMap("PredefinedDVBC.frq");
      for (int i = 0; i < COUNTRIES.length; i++)
      {
        java.util.Vector currList = (java.util.Vector) countryToRegionsMap.get(COUNTRIES[i]);
        if (currList != null)
        {
          DVBC_REGIONS[i] = (String[]) currList.toArray(Pooler.EMPTY_STRING_ARRAY);
        }
      }
      DVBS_REGIONS = buildRegionList("PredefinedDVBS.frq");
      loadedTuningFreqFiles = true;
    }
  }

  public static final boolean doesCountryHaveDVBTRegions(int countryCode)
  {
    loadTuningFreqFile();
    int idx = getCCIndex(countryCode);
    if (DVBT_REGIONS[idx] == null)
      return false;
    else
      return DVBT_REGIONS[idx].length > 0;
  }

  public static final String[] getDVBTRegionsForCountry(int countryCode)
  {
    loadTuningFreqFile();
    int idx = getCCIndex(countryCode);
    if (DVBT_REGIONS[idx] == null)
      return Pooler.EMPTY_STRING_ARRAY;
    else
      return (String[])DVBT_REGIONS[idx].clone();
  }

  public static final boolean doesCountryHaveDVBCRegions(int countryCode)
  {
    loadTuningFreqFile();
    int idx = getCCIndex(countryCode);
    if (DVBC_REGIONS[idx] == null)
      return false;
    else
      return DVBC_REGIONS[idx].length > 0;
  }

  public static final String[] getDVBCRegionsForCountry(int countryCode)
  {
    loadTuningFreqFile();
    int idx = getCCIndex(countryCode);
    if (DVBC_REGIONS[idx] == null)
      return Pooler.EMPTY_STRING_ARRAY;
    else
      return (String[])DVBC_REGIONS[idx].clone();
  }

  // DVB-S is not country-based
  public static final boolean doesCountryHaveDVBSRegions(int countryCode)
  {
    return true;
  }

  public static final String[] getDVBSRegionsForCountry(int countryCode)
  {
    loadTuningFreqFile();
    return (String[])DVBS_REGIONS.clone();
  }
}

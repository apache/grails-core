/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.gsp.TagLib


/**
 * Tags for rendering country selection / display of country names.
 *
 * @todo add ISO language codes too
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 */
@TagLib
class CountryTagLib implements TagLibrary {
    static final ISO3166_3 = [
        "afg":"Afghanistan",
        "alb":"Albania",
        "ata":"Antarctica",
        "dza":"Algeria",
        "asm":"American Samoa",
        "and":"Andorra",
        "ago":"Angola",
        "atg":"Antigua and Barbuda",
        "aze":"Azerbaijan",
        "arg":"Argentina",
        "aus":"Australia",
        "aut":"Austria",
        "bhs":"Bahamas",
        "bhr":"Bahrain",
        "bgd":"Bangladesh",
        "arm":"Armenia",
        "brb":"Barbados",
        "bel":"Belgium",
        "bmu":"Bermuda",
        "btn":"Bhutan",
        "bol":"Bolivia",
        "bih":"Bosnia and Herzegovina",
        "bwa":"Botswana",
        "bvt":"Bouvet Island",
        "bra":"Brazil",
        "blz":"Belize",
        "iot":"British Indian Ocean Territory",
        "slb":"Solomon Islands",
        "vgb":"British Virgin Islands",
        "brn":"Brunei Darussalam",
        "bgr":"Bulgaria",
        "mmr":"Myanmar",
        "bdi":"Burundi",
        "blr":"Belarus",
        "khm":"Cambodia",
        "cmr":"Cameroon",
        "can":"Canada",
        "cpv":"Cape Verde",
        "cym":"Cayman Islands",
        "caf":"Central African",
        "lka":"Sri Lanka",
        "tcd":"Chad",
        "chl":"Chile",
        "chn":"China",
        "twn":"Taiwan",
        "cxr":"Christmas Island",
        "cck":"Cocos (Keeling) Islands",
        "col":"Colombia",
        "com":"Comoros",
        "myt":"Mayotte",
        "cog":"Republic of the Congo",
        "cod":"The Democratic Republic Of The Congo",
        "cok":"Cook Islands",
        "cri":"Costa Rica",
        "hrv":"Croatia",
        "cub":"Cuba",
        "cyp":"Cyprus",
        "cze":"Czech Republic",
        "ben":"Benin",
        "dnk":"Denmark",
        "dma":"Dominica",
        "dom":"Dominican Republic",
        "ecu":"Ecuador",
        "slv":"El Salvador",
        "gnq":"Equatorial Guinea",
        "eth":"Ethiopia",
        "eri":"Eritrea",
        "est":"Estonia",
        "fro":"Faroe Islands",
        "flk":"Falkland Islands",
        "sgs":"South Georgia and the South Sandwich Islands",
        "fji":"Fiji",
        "fin":"Finland",
        "ala":"\u00C5land Islands",
        "fra":"France",
        "guf":"French Guiana",
        "pyf":"French Polynesia",
        "atf":"French Southern Territories",
        "dji":"Djibouti",
        "gab":"Gabon",
        "geo":"Georgia",
        "gmb":"Gambia",
        "pse":"Occupied Palestinian Territory",
        "deu":"Germany",
        "gha":"Ghana",
        "gib":"Gibraltar",
        "kir":"Kiribati",
        "grc":"Greece",
        "grl":"Greenland",
        "grd":"Grenada",
        "glp":"Guadeloupe",
        "gum":"Guam",
        "gtm":"Guatemala",
        "gin":"Guinea",
        "guy":"Guyana",
        "hti":"Haiti",
        "hmd":"Heard Island and McDonald Islands",
        "vat":"Vatican City State",
        "hnd":"Honduras",
        "hkg":"Hong Kong",
        "hun":"Hungary",
        "isl":"Iceland",
        "ind":"India",
        "idn":"Indonesia",
        "irn":"Islamic Republic of Iran",
        "irq":"Iraq",
        "irl":"Ireland",
        "isr":"Israel",
        "ita":"Italy",
        "civ":"C\u00F4te d'Ivoire",
        "jam":"Jamaica",
        "jpn":"Japan",
        "kaz":"Kazakhstan",
        "jor":"Jordan",
        "ken":"Kenya",
        "prk":"Democratic People's Republic of Korea",
        "kor":"Republic of Korea",
        "kwt":"Kuwait",
        "kgz":"Kyrgyzstan",
        "lao":"Lao People's Democratic Republic",
        "lbn":"Lebanon",
        "lso":"Lesotho",
        "lva":"Latvia",
        "lbr":"Liberia",
        "lby":"Libyan Arab Jamahiriya",
        "lie":"Liechtenstein",
        "ltu":"Lithuania",
        "lux":"Luxembourg",
        "mac":"Macao",
        "mdg":"Madagascar",
        "mwi":"Malawi",
        "mys":"Malaysia",
        "mdv":"Maldives",
        "mli":"Mali",
        "mlt":"Malta",
        "mtq":"Martinique",
        "mrt":"Mauritania",
        "mus":"Mauritius",
        "mex":"Mexico",
        "mco":"Monaco",
        "mng":"Mongolia",
        "mda":"Republic of Moldova",
        "msr":"Montserrat",
        "mar":"Morocco",
        "moz":"Mozambique",
        "omn":"Oman",
        "nam":"Namibia",
        "nru":"Nauru",
        "npl":"Nepal",
        "nld":"Netherlands",
        "bes":"Bonaire, Sint Eustatius and Saba",
        "cuw":"Curaçao",
        "sxm":"Sint Maarten (Dutch part)",
        "abw":"Aruba",
        "ncl":"New Caledonia",
        "vut":"Vanuatu",
        "nzl":"New Zealand",
        "nic":"Nicaragua",
        "ner":"Niger",
        "nga":"Nigeria",
        "niu":"Niue",
        "nfk":"Norfolk Island",
        "nor":"Norway",
        "mnp":"Northern Mariana Islands",
        "umi":"United States Minor Outlying Islands",
        "fsm":"Federated States of Micronesia",
        "mhl":"Marshall Islands",
        "plw":"Palau",
        "pak":"Pakistan",
        "pan":"Panama",
        "png":"Papua New Guinea",
        "pry":"Paraguay",
        "per":"Peru",
        "phl":"Philippines",
        "pcn":"Pitcairn",
        "pol":"Poland",
        "prt":"Portugal",
        "gnb":"Guinea-Bissau",
        "tls":"Timor-Leste",
        "pri":"Puerto Rico",
        "qat":"Qatar",
        "reu":"R\u00E9union",
        "rou":"Romania",
        "rus":"Russian Federation",
        "rwa":"Rwanda",
        "shn":"Saint Helena",
        "kna":"Saint Kitts and Nevis",
        "aia":"Anguilla",
        "lca":"Saint Lucia",
        "spm":"Saint-Pierre and Miquelon",
        "vct":"Saint Vincent and the Grenadines",
        "smr":"San Marino",
        "stp":"Sao Tome and Principe",
        "sau":"Saudi Arabia",
        "sen":"Senegal",
        "syc":"Seychelles",
        "sle":"Sierra Leone",
        "sgp":"Singapore",
        "svk":"Slovakia",
        "vnm":"Vietnam",
        "svn":"Slovenia",
        "som":"Somalia",
        "zaf":"South Africa",
        "zwe":"Zimbabwe",
        "esp":"Spain",
        "esh":"Western Sahara",
        "sdn":"Sudan",
        "sur":"Suriname",
        "sjm":"Svalbard and Jan Mayen",
        "swz":"Swaziland",
        "swe":"Sweden",
        "che":"Switzerland",
        "syr":"Syrian Arab Republic",
        "tjk":"Tajikistan",
        "tha":"Thailand",
        "tgo":"Togo",
        "tkl":"Tokelau",
        "ton":"Tonga",
        "tto":"Trinidad and Tobago",
        "are":"United Arab Emirates",
        "tun":"Tunisia",
        "tur":"Turkey",
        "tkm":"Turkmenistan",
        "tca":"Turks and Caicos Islands",
        "tuv":"Tuvalu",
        "uga":"Uganda",
        "ukr":"Ukraine",
        "mkd":"The Former Yugoslav Republic of Macedonia",
        "egy":"Egypt",
        "gbr":"United Kingdom",
        "imn":"Isle of Man",
        "tza":"United Republic Of Tanzania",
        "usa":"United States",
        "vir":"U.S. Virgin Islands",
        "bfa":"Burkina Faso",
        "ury":"Uruguay",
        "uzb":"Uzbekistan",
        "ven":"Venezuela",
        "wlf":"Wallis and Futuna",
        "wsm":"Samoa",
        "yem":"Yemen",
        "srb":"Serbia",
        "mne":"Montenegro",
        "zmb":"Zambia",
    ]

    // This needs to change, to sort on demand using the BROWSER's locale
    static final COUNTRY_CODES_BY_NAME_ORDER =
        ISO3166_3.entrySet().sort { a, b -> a.value.compareTo(b.value) }.collect() { it.key }
    static final COUNTRY_CODES_BY_NAME = new TreeMap()

    static {
        ISO3166_3.each { k, v ->
            COUNTRY_CODES_BY_NAME[v] = k
        }
    }

    /**
     * Display a country selection combo box.
     *
     * @emptyTag
     *
     * @attr from list of country codes or none for full list. Order is honoured
     * @attr valueMessagePrefix code prefix to use, if you want names of countries to come from message bundle
     * @attr value currently selected country code - ISO3166_3 (3 character, lowercase) form
     * @attr default currently selected country code - if value is null
     * @attr name the select name
     * @attr id the DOM element id - uses the name attribute if not specified
     * @attr keys A list of values to be used for the value attribute of each "option" element.
     * @attr optionKey By default value attribute of each <option> element will be the result of a "toString()" call on each element. Setting this allows the value to be a bean property of each element in the list.
     * @attr optionValue By default the body of each &lt;option&gt; element will be the result of a "toString()" call on each element in the "from" attribute list. Setting this allows the value to be a bean property of each element in the list.
     * @attr multiple boolean value indicating whether the select a multi-select (automatically true if the value is a collection, defaults to false - single-select)
     * @attr noSelection A single-entry map detailing the key and value to use for the "no selection made" choice in the select box. If there is no current selection this will be shown as it is first in the list, and if submitted with this selected, the key that you provide will be submitted. Typically this will be blank - but you can also use 'null' in the case that you're passing the ID of an object
     * @attr disabled boolean value indicating whether the select is disabled or enabled (defaults to false - enabled)
     */
    Closure countrySelect = { attrs ->
        if (!attrs.from) {
            attrs.from = COUNTRY_CODES_BY_NAME_ORDER
        }

        if (!attrs['valueMessagePrefix']) attrs.optionValue = { ISO3166_3[it] }

        if (!attrs.value) {
            attrs.value = attrs.remove('default')
        }
        out << select(attrs)
    }

    /**
     * Take a country code and output the country name, from the internal data.<br/>
     * Note: to use message bundle to resolve name, use g:message tag
     *
     * @emptyTag
     *
     * @attr code REQUIRED the ISO3166_3 country code
     */
    Closure country = { attrs ->
        if (!attrs.code) {
            throwTagError "[country] requires [code] attribute to specify the country code"
        }
        out << ISO3166_3[attrs.code]
    }
}

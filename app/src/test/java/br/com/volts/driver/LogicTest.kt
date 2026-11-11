package br.com.volts.driver

import br.com.volts.driver.offers.OfferParser
import br.com.volts.driver.pay.PixPayload
import br.com.volts.driver.stations.StationType
import br.com.volts.driver.stations.Stations
import br.com.volts.driver.tracking.DistanceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ---- Pix ----

    @Test fun crc16MatchesStandardCheckValue() {
        assertEquals(0x29B1, PixPayload.crc16("123456789"))
    }

    @Test fun pixPayloadHasExpectedFieldsAndValidCrc() {
        val p = PixPayload.build("chave@exemplo.com", "João da Silva Ação", "São Paulo", 2350)
        assertTrue(p.startsWith("000201" + "2639" + "0014br.gov.bcb.pix" + "0117chave@exemplo.com"))
        assertTrue(p.contains("520400005303986" + "540523.50" + "5802BR"))
        assertTrue(p.contains("5918JOAO DA SILVA ACAO" + "6009SAO PAULO" + "62070503***" + "6304"))
        assertEquals("%04X".format(PixPayload.crc16(p.dropLast(4))), p.takeLast(4))
    }

    @Test fun pixPayloadFormatsCentsBelowTen() {
        assertTrue(PixPayload.build("k", "N", "C", 1205).contains("540512.05"))
    }

    // ---- Ofertas ----

    @Test fun parsesOfferSummingPickupAndTrip() {
        val o = OfferParser.parse("UberX\nR$ 18,75\n4 min (1,2 km) de distância\nViagem de 15 min (7,3 km)\nAceitar")!!
        assertEquals(1875, o.valueCents)
        assertEquals(8.5, o.km, 1e-9)
        assertEquals(19, o.minutes)
        assertEquals(18.75 / 8.5, o.brlPerKm!!, 1e-9)
    }

    @Test fun ignoresPerKmRateAndParsesWholeReais() {
        val o = OfferParser.parse("R$ 2,10/km\nR$ 1.250\n3 min · 0.8 km\n40 min · 30 km")!!
        assertEquals(125_000, o.valueCents)
        assertEquals(30.8, o.km, 1e-9)
    }

    @Test fun screensWithoutDistanceOrTimeAreNotOffers() {
        assertNull(OfferParser.parse("Ganhos de hoje\nR$ 230,00\n12 viagens"))
        assertNull(OfferParser.parse("Você está online\nProcurando viagens"))
        assertNull(OfferParser.parse("R$ 15,00\n5 km"))
    }

    // ---- Distância ----

    @Test fun tracksMovementAndIgnoresNoise() {
        val t = DistanceTracker()
        assertEquals(0.0, t.onFix(-23.5500, -46.6300, 10f, 0), 0.0)
        // parado: variação de ~3 m
        assertEquals(0.0, t.onFix(-23.55002, -46.63002, 10f, 3_000), 0.0)
        // leitura imprecisa
        assertEquals(0.0, t.onFix(-23.5600, -46.6300, 80f, 6_000), 0.0)
        // ~111 m ao norte em 9 s
        assertEquals(111.2, t.onFix(-23.5490, -46.6300, 10f, 9_000), 1.0)
        // salto impossível (11 km em 3 s) é descartado, mas vira a nova referência
        assertEquals(0.0, t.onFix(-23.4490, -46.6300, 10f, 12_000), 0.0)
        assertEquals(111.2, t.onFix(-23.4480, -46.6300, 10f, 20_000), 1.0)
    }

    // ---- Postos ----

    @Test fun parsesOverpassResponse() {
        val json = """
            {"elements":[
              {"type":"node","id":1,"lat":-23.56,"lon":-46.63,"tags":{"amenity":"fuel","brand":"Ipiranga","opening_hours":"24/7"}},
              {"type":"way","id":2,"center":{"lat":-23.551,"lon":-46.63},"tags":{"amenity":"charging_station","name":"Shopping Centro","operator":"Tupi","capacity":"4","socket:type2":"2","socket:type2_combo":"2"}},
              {"type":"node","id":3,"lat":-23.60,"lon":-46.63,"tags":{"amenity":"charging_station","operator":"Volts Brasil"}},
              {"type":"node","id":4,"lat":-23.55,"lon":-46.63,"tags":{"amenity":"cafe"}},
              {"type":"way","id":5,"tags":{"amenity":"fuel"}}
            ]}
        """
        val list = Stations.parse(json, -23.55, -46.63)
        assertEquals(listOf(3L, 2L, 1L), list.map { it.id }) // Volts primeiro, depois por distância

        val volts = list[0]
        assertTrue(volts.isVolts)
        assertEquals("Volts Brasil", volts.name)

        val ev = list[1]
        assertEquals(StationType.EV, ev.type)
        assertEquals("Shopping Centro", ev.name)
        assertEquals("Tupi · 4 vagas · Tipo 2/CCS2", ev.details)
        assertEquals(111.0, ev.distanceM, 2.0)
        assertFalse(ev.isVolts)

        val fuel = list[2]
        assertEquals("Ipiranga", fuel.name)
        assertEquals("24h", fuel.details)
    }
}

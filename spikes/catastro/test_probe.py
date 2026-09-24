import unittest

from probe import parcels, utm_area


class ParcelGmlTest(unittest.TestCase):
    def test_surface_polygon_patch_keeps_exterior_and_hole(self):
        body = b'''<FeatureCollection xmlns:cp="http://inspire.ec.europa.eu/schemas/cp/4.0"
            xmlns:gml="http://www.opengis.net/gml/3.2">
            <cp:CadastralParcel>
              <cp:areaValue>12</cp:areaValue>
              <cp:nationalCadastralReference>23044A00400021</cp:nationalCadastralReference>
              <cp:geometry><gml:MultiSurface><gml:surfaceMember><gml:Surface>
                <gml:patches><gml:PolygonPatch>
                  <gml:exterior><gml:LinearRing><gml:posList>0 0 4 0 4 4 0 4 0 0</gml:posList></gml:LinearRing></gml:exterior>
                  <gml:interior><gml:LinearRing><gml:posList>1 1 1 3 3 3 3 1 1 1</gml:posList></gml:LinearRing></gml:interior>
                </gml:PolygonPatch></gml:patches>
              </gml:Surface></gml:surfaceMember></gml:MultiSurface></cp:geometry>
            </cp:CadastralParcel></FeatureCollection>'''
        result = parcels(body)
        self.assertEqual(result[0][0], "23044A00400021")
        self.assertEqual(len(result[0][2]), 1)
        self.assertEqual(len(result[0][2][0]), 2)
        self.assertEqual(utm_area(result[0][2]), 12)

    def test_polygon_fallback(self):
        body = b'''<FeatureCollection xmlns:cp="http://inspire.ec.europa.eu/schemas/cp/4.0"
            xmlns:gml="http://www.opengis.net/gml/3.2"><cp:CadastralParcel>
            <cp:nationalCadastralReference>23044A00400021</cp:nationalCadastralReference>
            <cp:geometry><gml:Polygon><gml:exterior><gml:LinearRing>
            <gml:posList>0 0 1 0 1 1 0 1 0 0</gml:posList>
            </gml:LinearRing></gml:exterior></gml:Polygon></cp:geometry>
            </cp:CadastralParcel></FeatureCollection>'''
        self.assertEqual(len(parcels(body)[0][2]), 1)


if __name__ == "__main__":
    unittest.main()

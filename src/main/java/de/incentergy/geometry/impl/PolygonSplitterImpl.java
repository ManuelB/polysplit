package de.incentergy.geometry.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.Polygon;

import de.incentergy.geometry.PolygonSplitter;
import de.incentergy.geometry.impl.EdgePair.EdgePairSubpolygons;
import de.incentergy.geometry.utils.GeometryUtils;

/**
 * {@link PolygonSplitter} implementation based on the algorithm by Sumit Khetarpal
 *
 * @see <a href="http://www.khetarpal.org/polygon-splitting/">Dividing A Polygon In Any Given Number Of Equal Areas</a>
 */
public class PolygonSplitterImpl {

    private final Polygon originalPolygon;
    private final int numberOfParts;
    private final double singlePartArea;

    public PolygonSplitterImpl(Polygon originalPolygon, int numberOfParts) {
        if (!originalPolygon.isValid()) {
            throw new IllegalArgumentException("Polygon is not valid!");
        }
        if (numberOfParts < 2) {
            throw new IllegalArgumentException("Number of parts should be greater than 1!");
        }
        this.originalPolygon = originalPolygon;
        this.numberOfParts = numberOfParts;
        this.singlePartArea = originalPolygon.getArea() / numberOfParts;
    }

    public List<Polygon> split() {
        // TODO: add validation - at least 4 sides, no holes

        List<Polygon> polygonParts = new ArrayList<>(numberOfParts);
        Polygon remainingPoly = originalPolygon;
        for (int i = 0; i < numberOfParts - 1; i++) {
            remainingPoly = split(remainingPoly, polygonParts);
        }
        polygonParts.add(remainingPoly);

        // TODO: remove sanity checks

        // sanity check: total area is the same
        double totalAreaOfTheParts = polygonParts.stream().mapToDouble(Polygon::getArea).sum();
        if (!GeometryUtils.equalWithinDelta(totalAreaOfTheParts, originalPolygon.getArea())) {
            throw new IllegalStateException("Area of the parts does not match original area");
        }

        // sanity check: geometry is the same
        GeometryCollection col = new GeometryFactory().createGeometryCollection(polygonParts.toArray(new Geometry[polygonParts.size()]));

        Polygon unionOfTheParts =         (Polygon) col.union();
//        Polygon unionOfTheParts = polygonParts.stream().reduce(( Geomep1, p2) -> p1.union(p2)).get();
        if (unionOfTheParts.equalsNorm(originalPolygon)) {
            throw new IllegalStateException("The sum of the parts is not equal to the original polygon");
        }

        return Collections.unmodifiableList(polygonParts);
    }

    private Polygon split(Polygon polygon, List<Polygon> resultList) {
        List<LineSegment> segments = GeometryUtils.getLineSegments(polygon.getExteriorRing());

        List<Cut> possibleCuts = new ArrayList<>();

        // for each unique edge pair
        for (int i = 0; i < segments.size() - 2; i++) {

            // generate unique edge pairs (e.g. 2 pairs for any rectangle)
            for (int j = i + 2; j < segments.size(); j++) {
                int segmentsCovered = j - i + 1;            // number of segments covered by a LineRing starting with edgeA and ending with edgeB (including)
                if (segments.size() == segmentsCovered) {
                    break;
                }

                LineSegment edgeA = segments.get(i);
                LineSegment edgeB = segments.get(j);
                EdgePair edgePair = new EdgePair(edgeA, edgeB);
                EdgePairSubpolygons subpolygons = edgePair.getSubpolygons();
                List<Cut> cutForCurrentEdgePair = subpolygons.getCuts(polygon, singlePartArea);
                possibleCuts.addAll(cutForCurrentEdgePair);
            }
        }

        // greedy algorithm: take minimum cut length
        Cut shortestCut = possibleCuts.stream().min(Comparator.comparing(Cut::getLength)).get();
        resultList.add(shortestCut.getCutAway());

        return (Polygon) polygon.difference(shortestCut.getCutAway());
    }

}

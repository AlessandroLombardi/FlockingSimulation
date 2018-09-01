package flocking.model;

import java.util.List;

/**
 * An {@link Unit} decorator used to perform the separation rule.
 */
public class UnitSeparation extends UnitImpl implements Unit {

    private final Unit unit;

    private static final double MIN_SEPARATION_DISTANCE = 10;
    private static final double MAX_SEPARATION = 60;

    /**
     * @param unit the base of this decorator
     */
    public UnitSeparation(final Unit unit) {
        super(unit.getPosition(), unit.getSideLength(), unit.getSpeed());
        this.unit = unit;
    }

    @Override
    public final Vector2D getSteeringForce() {
        return new Vector2DImpl(this.unit.getSteeringForce().sumVector(this.separate()));
    }

    /**
     * @return the separation rule steering force
     */
    private Vector2D separate() {
        final List<Entity> neighbors = Simulation.getNeighbors(this.getCohesionArea(), this);
        if (neighbors.isEmpty()) {
            return new Vector2DImpl(0, 0);
        }

        Vector2D separationForce = new Vector2DImpl(0, 0);
        for (final Entity n : neighbors) {
            if (n.getPosition().distance(this.getPosition()) < UnitSeparation.MIN_SEPARATION_DISTANCE) {
                separationForce = separationForce.sumVector((n.getPosition().sumVector(this.getPosition().mulScalar(-1))).mulScalar(-1));
            }
        }

        return separationForce.mulScalar(UnitSeparation.MAX_SEPARATION);
    }
}

package flocking.model;


import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * An implementation of {@link Entity}.
 */
public class UnitImpl implements Unit {

    private Vector2D position;
    private double angle;
    private Vector2D speed;

    private int timer;
    private static final int MAX_TIMER = 400;

    //150 , -295 :: 30 , -330 :: 90 , -270 :: 0 , -360
    private static final double MIN_ANGLE = 150;
    private static final double MAX_ANGLE = -295;
    private static final int DELTA_ANGLE = 20;

    private static final int AREA = 15;
    private static final double MAX_FORCE = 200;
    private static final double MAX_SPEED = 200;

    private static final double MAX_SIGHT = 15;
    private static final double MAX_AVOIDANCE = 200;

    private static final double MAX_COHESION = 60;
    private static final double MIN_COHESION_DISTANCE = 10;

    private final int sideLength;
    private final List<Vector2D> figure;
    private final double mass;

    //Behaviour
    private boolean isWander;

    /**
     * @param startPos the first {@link Unit}'s {@link Point}
     * @param sideLength the {@link Unit} length
     * @param speed the {@link Unit} speed
     */
    public UnitImpl(final Vector2D startPos, final int sideLength, final Vector2D speed) {
        this.position = new Vector2DImpl(startPos);
        this.speed = new Vector2DImpl(speed);
        this.angle = Math.toDegrees(Math.atan2(speed.getY(), speed.getX()));
        this.mass = 10;

        this.timer = UnitImpl.MAX_TIMER;

        this.sideLength = sideLength;
        this.figure = new ArrayList<>();
    }

    @Override
    public final List<Vector2D> getFigure() {
        this.figure.clear();
        this.figure.add(new Vector2DImpl(this.position.getX() - this.sideLength / 2, this.position.getY() - this.sideLength / 2));
        this.figure.add(new Vector2DImpl(this.position.getX() - this.sideLength / 2, this.position.getY() + this.sideLength / 2));
        this.figure.add(new Vector2DImpl(this.position.getX() + this.sideLength, this.position.getY()));
        return Collections.unmodifiableList(this.figure);
    }

    @Override
    public final void setFigure(final List<Vector2D> figure) {
        Collections.copy(this.figure, figure);
    }

    @Override
    public final Vector2D getPosition() {
        return this.position;
    }

    @Override
    public final double getAngle() {
        return this.angle;
    }

    @Override
    public final void setPosition(final Vector2D position) {
        this.position = new Vector2DImpl(position);
    }

    @Override
    public final void update(final float elapsed) {
        if (this.timer <= 0) {
            Vector2D result = new Vector2DImpl(0, 0);
            //Sum all steering forces
            if (this.isWander) {
                result = result.sumVector(this.wander());
            } else {
                result = result.sumVector(this.seek());
            }

            result = result.sumVector(this.obstacleAvoidance());
            result = result.sumVector(this.cohesion());
            result = result.sumVector(this.align());
            result = result.sumVector(this.separate());

            result = result.clampMagnitude(UnitImpl.MAX_FORCE);
            result = result.mulScalar(1 / this.mass);

            final Vector2D finalSpeed = this.speed.sumVector(result).clampMagnitude(UnitImpl.MAX_SPEED);

            this.angle = Math.toDegrees(Math.atan2(finalSpeed.getY(), finalSpeed.getX()));
            if (angle < 0) {
                angle += 360;
            } else if (angle > 360) {
                angle -= 360;
            }

            this.position = this.position.sumVector(finalSpeed);
            this.speed = this.speed.setAngle(angle);

            this.timer = UnitImpl.MAX_TIMER;
        } else {
            this.timer -= elapsed;
        }
    }

    @Override
    public final Rectangle getArea(final double growFactor) {
        return new Rectangle((int) this.position.getX() - sideLength * AREA / 2,
                (int) this.position.getY() - sideLength * AREA / 2,
                sideLength * AREA,
                sideLength * AREA);
    }

    @Override
    public final Shape getCohesionArea() {
        final Rectangle rectangle = this.getArea(AREA);

        final Shape arc = new Arc2D.Double(rectangle.x,
                rectangle.y, 
                rectangle.width, 
                rectangle.height, 
                MIN_ANGLE, 
                MAX_ANGLE, 
                Arc2D.PIE);

        final Path2D.Double path = new Path2D.Double();
        path.append(arc, false);

        final AffineTransform at = AffineTransform.getRotateInstance(Math.toRadians(angle), this.position.getX(), this.position.getY());
        path.transform(at);

        return path;
    }

    @Override
    public final Vector2D getSpeed() {
        return new Vector2DImpl(this.speed);
    }

    @Override
    public final void toogleWander() {
        this.isWander = !this.isWander;
    }

    /**
     * @return the wander steering forces
     */
    private Vector2D wander() {
        final Random rnd = new Random();
        double deltaAngle = rnd.nextInt(UnitImpl.DELTA_ANGLE);
        if (rnd.nextBoolean()) {
            deltaAngle = -rnd.nextInt(UnitImpl.DELTA_ANGLE);
        }

        Vector2D center = new Vector2DImpl(this.speed);
        center = center.normalize().mulScalar(this.sideLength * 2);

        center = center.rotate(deltaAngle);

        return center;
    }

    /**
     * @return the seek steering forces
     */
    private Vector2D seek() {
        final Vector2D target = new Vector2DImpl(Simulation.TARGET.getPosition().getX(), 
                Simulation.TARGET.getPosition().getY());

        return (target.sumVector(this.position.mulScalar(-1)).normalize().mulScalar(UnitImpl.MAX_SPEED * 1f / 4f)).sumVector(this.speed.mulScalar(-1));
    }

    /**
     * @return the collision avoidance steering force
     */
    private Vector2D obstacleAvoidance() {
        final List<Entity> obstacles = Simulation.getObstacleInPath(this.getLine(), this);
        if (obstacles.isEmpty()) {
            return new Vector2DImpl(0, 0);
        }

        final Optional<Entity> obstacle = obstacles.stream().min((o1, o2) -> {
            return o1.getPosition().distance(this.position) > o2.getPosition().distance(this.position) 
                    ? 1 : o1.getPosition().distance(this.position) == o2.getPosition().distance(this.position) 
                    ? 0 : -1;
        });

        if (!obstacle.isPresent()) {
            return new Vector2DImpl(0, 0);
        }

        final Vector2D sight = this.speed.normalize().mulScalar(UnitImpl.MAX_SIGHT).sumVector(this.position);
        final Vector2D avoidanceForce = sight.sumVector(obstacle.get().getPosition().mulScalar(-1));
        return avoidanceForce.normalize().mulScalar(MAX_AVOIDANCE);
    }

    /**
     * @return a {@link Line2D} representing the sight
     */
    public Line2D.Double getLine() {
        final Vector2D sight = this.speed.normalize().mulScalar(UnitImpl.MAX_SIGHT).sumVector(this.position);
        return new Line2D.Double(new Point((int) Math.round(this.position.getX()), (int) Math.round(this.position.getY())),
                new Point((int) Math.round(sight.getX()), (int) Math.round(sight.getY())));

    }

    /**
     * @return the cohesion rule steering force
     */
    private Vector2D cohesion() {
        final List<Entity> neighbors = Simulation.getNeighbors(this.getCohesionArea(), this);
        if (neighbors.isEmpty()) {
            return new Vector2DImpl(0, 0);
        }

        double counter = 0;
        Vector2D centroid = new Vector2DImpl(0, 0);
        for (final Entity n : neighbors) {
            if (n.getPosition().distance(this.getPosition()) > UnitImpl.MIN_COHESION_DISTANCE) {
                centroid = centroid.sumVector(n.getPosition());
                counter++;
            }
        }

        if (counter != 0) {
            centroid = centroid.mulScalar(1 / counter);
            final Vector2D cohesionForce = centroid.sumVector(this.position.mulScalar(-1));
            return cohesionForce.normalize().mulScalar(UnitImpl.MAX_COHESION);
        }

        return new Vector2DImpl(0, 0);
    }

    /**
     * @return the alignment rule steering force
     */
    private Vector2D align() {
        final List<Entity> neighbors = Simulation.getNeighbors(this.getCohesionArea(), this);
        if (neighbors.isEmpty()) {
            return new Vector2DImpl(0, 0);
        }

        double counter = 0;
        Vector2D averageSpeed = new Vector2DImpl(0, 0);
        for (final Entity n : neighbors) {
            if (n.getPosition().distance(this.getPosition()) > UnitImpl.MIN_COHESION_DISTANCE) {
                averageSpeed = averageSpeed.sumVector(n.getPosition());
                counter++;
            }
        }

        if (counter != 0) {
            averageSpeed = averageSpeed.mulScalar(1 / counter);
            final Vector2D cohesionForce = averageSpeed.sumVector(this.speed.mulScalar(-1));
            return cohesionForce.normalize().mulScalar(1f / 2f);
        }

        return new Vector2DImpl(0, 0);
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
            if (n.getPosition().distance(this.getPosition()) < UnitImpl.MIN_COHESION_DISTANCE) {
                separationForce = separationForce.sumVector((n.getPosition().sumVector(this.getPosition().mulScalar(-1))).mulScalar(-1));
            }
        }

        return separationForce.mulScalar(MAX_COHESION * 2f / 3f);
    }
}

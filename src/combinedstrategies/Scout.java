package combinedstrategies;

import battlecode.common.*;

public class Scout extends RobotPlayer implements RobotHandler {

    private int strategy = 0; // Defining which state the robot is in.
    private int myTreeID = 0; // ID of the tree it tries to hide in
    private int myOpponentID = 0; // Keep chasing the same gardener

    private int idle = 0;


    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void onLoop() throws GameActionException {
        if (strategy == 0) {
            tryHarassGardener();
        } else if (strategy == 1) {
            tryMoveOntoTree();
            tryAttackEnemy();
            if (!rc.hasMoved() && !rc.hasAttacked()) {
                idle++;
                if (idle > 200) { // Some time it sits there too long
                    strategy = 2;
                }
            }
        } else {
            tryMoveTowardEnemy();
            tryAttackEnemy();
        }
    }

    @Override
    public void reportUnitCount() throws GameActionException {
        if (Messaging.shouldSendHeartbeat()) {
            Messaging.sendHeartbeatSignal(0, 0, 0, 1, 0, 0, 0, 0f);
        }
    }

    private void tryHarassGardener() throws GameActionException {
        if (myOpponentID != 0 && rc.canSenseRobot(myOpponentID)) {
            Direction dir = new Direction(rc.getLocation(), rc.senseRobot(myOpponentID).getLocation());
            if (rc.canFireSingleShot() && gen.nextDouble() < 0.8) {
                // Fire shot towards the gardener, sometimes not to move without being shot
                rc.fireSingleShot(dir);
            } else {
                tryMove(dir, 10, 5); // Move closer towards the gardener
            }
        } else { // No opponent ID being registered
            RobotInfo[] opponents = rc.senseNearbyRobots(10.0f, them);
            if (opponents.length == 0) {
                tryMoveTowardEnemyArchons(); // No enemy spotted, go ahead find someone
            } else {
                for (RobotInfo robotSpotted : opponents) {
                    if (robotSpotted.getType() == RobotType.GARDENER) {
                        myOpponentID = robotSpotted.getID(); // select target
                        tryMove(new Direction(rc.getLocation(), robotSpotted.getLocation()), 10, 5);
                        break;
                    }
                }
            }
            tryAttackEnemy(); // Random attack
        }
        if (!rc.hasMoved()) {
            tryMove(randomDirection(), 20, 10); // no gardener found, try any direction loop around
        }

        if (rc.getHealth() < 10 && strategy == 0 && idle < 10) { // Lost half of its life
            strategy = 1; // change strategy, Try hit and run now
        }

    }

    private boolean tryMoveOntoTree() throws GameActionException {
        if (myTreeID != 0 && rc.canSenseTree(myTreeID)) {
            MapLocation treeLoc = rc.senseTree(myTreeID).getLocation();
            if (rc.canMove(treeLoc)) {
                rc.move(rc.senseTree(myTreeID).getLocation());
                // TODO: Telling other scout that this tree is occupied
                // Now just be annoying

                return false;
            }
        }
        // Need new TreeID assignment
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10);
        if (nearbyTrees.length > 0) {
            MapLocation my_loc = rc.getLocation();
            TreeInfo bestTree = nearbyTrees[0];
            float closest = 10000;
            for (TreeInfo tree : nearbyTrees) {
                float dis = tree.getLocation().distanceTo(my_loc);
                MapLocation closestPointToCenter;
                Direction dirToTree = my_loc.directionTo(tree.getLocation());
                // if the center of the tree is outside the sensor range, at least try to view the edge
                if (dis < type.sensorRadius) {
                    closestPointToCenter = tree.getLocation();
                } else {
                    // due to finite precision of floats, we need to clip this slightly
                    float rangeExclusive = type.sensorRadius - 0.0001f;
                    closestPointToCenter = my_loc.add(dirToTree, rangeExclusive);
                }
                if (dis < closest && tree.getTeam() == them && !rc.isLocationOccupiedByRobot(closestPointToCenter) && rc
                        .canMove(dirToTree)) {
                    // Good choice if its close, tree belongs to the opponent, and its not occupied
                    closest = dis;
                    bestTree = tree;
                }
            }
            if (closest != 10000) { // Found a good tree to hide
                Direction dirToTree = rc.getLocation().directionTo(bestTree.getLocation());
                rc.setIndicatorLine(bestTree.getLocation(), rc.getLocation(), 0, 0, 255);
                myTreeID = bestTree.getID();
                rc.move(dirToTree);
            } else { // No tree to hide, let's dodge
                tryDodge();
            }


        }

        return true;
    }

    private boolean tryDodge() throws GameActionException {
        // jd: actually take bullets into account, for now just dodge the closest one
        BulletInfo bullets[] = rc.senseNearbyBullets(3); // only when its about to hit
        if (bullets.length > 0) {
            float closest = 3;
            BulletInfo bulletincoming = bullets[0];
            MapLocation my_loc = rc.getLocation();
            for (BulletInfo singlebullet : bullets) {
                float dis = singlebullet.getLocation().distanceTo(my_loc);
                if (dis < closest) { // Get the closest bullet
                    closest = dis;
                    bulletincoming = singlebullet;
                }
            }
            Direction bulletandme = new Direction(bulletincoming.getLocation(), my_loc);
            // The robot decides, depending on the first incoming shot,
            // to move in the direction orthogonal to the direct line between bullet and itself
            // It merely decides in which direction along the lines
            if (bulletincoming.getDir().degreesBetween(bulletandme) > 0) {
                // to determine which direction to hide
                bulletandme.rotateRightDegrees((float) (StrictMath.PI / 2.0));
            } else {
                bulletandme.rotateLeftDegrees((float) (StrictMath.PI / 2.0));
            }
            tryMove(bulletandme, 5, 2);
            return true;
        }
        return false;

//        if(rc.senseNearbyRobots(7.0f, them).length > 0){
//            // Move randomly
//            tryMove(randomDirection(), 20, 10);
//            return true;
//        }
//        return false;
    }


    private boolean tryMoveTowardEnemy() throws GameActionException {
        // since we're melee, just move toward an enemy
        // preferably one that can't outrun us

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, them);

        if (enemies.length == 0) {
            return false;
        }

        RobotInfo closestSlowEnemy = null;
        RobotInfo closestFastEnemy = null;
        float slowEnemyDist = Float.MAX_VALUE;
        float fastEnemyDist = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.getLocation());
            if (enemy.getType().strideRadius > type.strideRadius) {
                if (dist < fastEnemyDist) {
                    fastEnemyDist = dist;
                    closestFastEnemy = enemy;
                }
            } else {
                if (dist < slowEnemyDist) {
                    slowEnemyDist = dist;
                    closestSlowEnemy = enemy;
                }
            }
        }

        RobotInfo target = closestSlowEnemy;
        if (closestFastEnemy != null) {
            target = closestFastEnemy;
        }

        if (target == null) {
            return false;
        }

        Direction dir = rc.getLocation().directionTo(target.getLocation());

        return tryMove(dir, 45, 10);
    }

    private double evaluateEnemy(RobotInfo enemy) {
        // things to consider:
        // -health
        // -value (dps, dps*hp, isArchon) of enemy robot
        // -distance / dodgeabililty
        // -clusters of enemies

        // for now, let's use -health*dist
        // health for the obvious reason of attacking the weakest
        // dist to estimate dodgeability, because the bullet density of a circle of bullets decreases linearly with the
        // radius
        // (minus sign, just to make it max instead of min)
        return -enemy.getHealth() * rc.getLocation().distanceTo(enemy.getLocation());
    }

    private boolean tryAttackEnemy() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, them);

        if (robots.length == 0) {
            return false;
        }

        if (!rc.canFireSingleShot()) {
            return false;
        }

        // pick an enemy somehow
        double maxEnemyScore = Double.NEGATIVE_INFINITY;
        RobotInfo bestTarget = null;
        for (RobotInfo enemy : robots) {
            // we should also check if there is an unobstructed path to the enemy from here
            // unfortunately, that's complicated. maybe collect all nearby robots and sort by angle? that way we can
            // binary search these kinds of queries.
            if (enemy.type != RobotType.GARDENER || isPathToRobotObstructed(enemy)) {
                continue;
            }
            double score = evaluateEnemy(enemy);
            if (score > maxEnemyScore) {
                maxEnemyScore = score;
                bestTarget = enemy;
            }
        }

        if (bestTarget != null) {
            rc.fireSingleShot(rc.getLocation().directionTo(bestTarget.location));
            return true;
        }
        return false;
    }

}

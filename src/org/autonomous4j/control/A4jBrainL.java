/*
 * The MIT License
 *
 * Copyright 2015 Mark A. Heckler
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.autonomous4j.control;

import java.util.ArrayList;
import org.autonomous4j.interfaces.A4jBrain2D;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autonomous4j.listeners.xy.A4jLandListener;
import org.autonomous4j.physical.A4jLandController;
import org.autonomous4j.tracking.A4jBlackBox;
import org.autonomous4j.tracking.A4jBlackBox.Movement;

/**
 *
 * @author Mark Heckler (mark.heckler@gmail.com, @mkheck)
 */
public class A4jBrainL implements A4jBrain2D {
    private static final A4jBrainL brain = new A4jBrainL();
    private final A4jLandController controller = new A4jLandController();
    private final List<A4jLandListener> listeners = new ArrayList<>();
    //private NavData currentNav;
    //private final A4jBlackBox recorder;
    private boolean isRecording;
    
    public enum Direction {LEFT, RIGHT, FORWARD};

    private A4jBrainL() {
        //this.recorder = new A4jBlackBox();
        isRecording = true;
    }

    public static A4jBrainL getInstance() {
        return brain;
    }

    @Override
    public boolean connect() {
        try {
            controller.connect();
            // Local MQTT server
            listeners.add(new A4jLandListener());
            
            // Remote MQTT cloud servers
//            listeners.add(new A4jLandListener("tcp://m11.cloudmqtt.com:14655")
//                    .setUserName("<userID>")
//                    .setPassword("<password>"));
            
            listeners.add(new A4jLandListener("tcp://iot.eclipse.org:1883"));
            
            listeners.stream().forEach((listener) -> controller.addObserver(listener.connect()));

        } catch (Exception ex) {
            System.err.println("Exception creating new drone connection: " + ex.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public void disconnect() {
        if (controller != null) {
            if (!listeners.isEmpty()) {
                listeners.stream().forEach((listener) -> listener.disconnect());
                // After disconnecting the listeners from their respective MQTT 
                // servers, we now delete the controller's references to them.
                controller.deleteObservers();
            }
            
            controller.disconnect();
        }
        //recorder.shutdown();
    }

    /**
     * Convenience (pass-through) method for more fluent API.
     * @param ms Long variable specifying a number of milliseconds.
     * @return A4jBrainL object (allows command chaining/fluency.
     * @see #hold(long)
     */
    @Override
    public A4jBrainL doFor(long ms) {
        return hold(ms);
    }
    
    @Override
    public A4jBrainL hold(long ms) {
        System.out.println("Brain.hold for " + ms + " milliseconds...");
        try {
            Thread.sleep(ms);
            if (isRecording) {
                //recorder.recordDuration(ms);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Logger.getLogger(A4jBrainL.class.getName()).log(Level.SEVERE, null, e);
        }
        
        return this;
    }

    @Override
    public A4jBrainL stay() {
        System.out.println("Brain.stay");
        controller.stop();
        if (isRecording) {
            //recorder.recordAction(A4jBlackBox.Action.STAY);
        }
        
        return this;
    }

    @Override
    public A4jBrainL forward(long distance) {
        System.out.println("Brain.forward");
        if (isRecording) {
            //recorder.recordAction(A4jBlackBox.Action.FORWARD, speed);
        }

        controller.forward(distance);
        return this;
    }

    @Override
    public A4jBrainL backward(long distance) {
        System.out.println("Brain.backward");
        if (isRecording) {
            //recorder.recordAction(A4jBlackBox.Action.BACKWARD, speed);
        }

        controller.back(distance);
        return this;
    }
    
    public A4jBrainL patrol() {
        long distF, distL, distR;

        // Save this so we can finish here (more or less)
        distL = controller.pingLeft();
        distR = controller.pingRight();
        distF = controller.pingForward();

        // The rest of the pattern is identical until the final positioning movement
        // We do capture the initial position of the drone from the wall, however

        for (int i=0; i<4;i++) {
            forward(20);

            distL = controller.pingLeft();
            distR = controller.pingRight();
            controller.pingForward();
            turn(distL < distR ? Direction.LEFT : Direction.RIGHT, 180L);
        }        

        return this;        
    }
    
    /*
        This will choose "best" direction based upon how close 
    */
    public A4jBrainL patrolPerimeter() {
        long distF, distL, distR, distFromWall, distToCorner;
        Direction startDir, turnDir;
        final long STOP_DIST = 40;
        
        distL = controller.pingLeft();
        distR = controller.pingRight();
        distF = controller.pingForward();

        // ClosER wall wins the prize
        turnDir = distL < distR ? Direction.LEFT : Direction.RIGHT;
        // ClosEST wall wins the grand prize
        startDir = distF < Math.min(distL, distR) ? Direction.FORWARD : turnDir;
        
        if (startDir != Direction.FORWARD) {
            // Turn to face target (closest) wall
            turn(turnDir);
        }
        // The rest of the pattern is identical until the final positioning movement
        // We do capture the initial position of the drone from the wall, however
        distFromWall = pingMove(STOP_DIST);
        turn(turnDir);
        distToCorner = pingMove(STOP_DIST);
        turn(turnDir);
        pingMove(STOP_DIST);
        turn(turnDir);
        pingMove(STOP_DIST);
        turn(turnDir);
        pingMove(STOP_DIST);
        turn(turnDir);
        pingMove(distToCorner);
        turn(turnDir);
        pingMove(distFromWall);
        turn(turnDir);          // Turn 180 degrees to regain initial bearing
        if (startDir == Direction.FORWARD) {
            turn(turnDir);
        }
        
        return this;
    }

    /*
        This will choose "best" direction based upon how far away
    */
    public A4jBrainL patrolBlanket() {
        long distF, distL, distR, distFromWall, distToCorner;
        Direction dir;
        final long STOP_DIST = 50;
        
        for (int i=0; i<5;i++) {
            distL = controller.pingLeft();
            distR = controller.pingRight();
            distF = controller.pingForward();

            // Furthest wall wins the prize & determines best direction for turn
            dir = distL > distR ? Direction.LEFT : Direction.RIGHT;
        
            // If ALVIN finds himself in a corner, he does a 180, then re-evaluates
            if (Math.max(distF, Math.max(distL, distR)) < STOP_DIST) {
                turn(dir, 180);
            } else {
                dir = distF > Math.max(distL, distR) ? Direction.FORWARD : dir;

                if (dir != Direction.FORWARD) {
                    turn(dir);
                }
                distFromWall = pingMove(STOP_DIST);
            }
        }
        
        return this;
    }
    
    private Long pingMove(Long stopDistance) {
        Long distance = controller.pingForward();

        forward((distance - stopDistance) > 0 ? 
                distance - stopDistance : 
                0);    // Stop specified distance (in cm) from wall        
        
        return distance;
    }
    
    public A4jBrainL doBox(A4jBrainL.Direction dir, long cmMaxDistance) {
        forward(cmMaxDistance/2); // N center of box
        turn(dir);
        forward(cmMaxDistance/2); // To NW corner
        turn(dir);
        forward(cmMaxDistance); // To SW corner
        turn(dir);
        forward(cmMaxDistance); // To SE corner
        turn(dir);
        forward(cmMaxDistance); // To NE corner
        turn(dir);
        forward(cmMaxDistance/2); // Return to N center
        turn(dir);
        forward(cmMaxDistance/2); // Return to box center (approximadamente)
        turn(dir);  // Turn twice to return to original bearing (mas o menos)
        turn(dir);
        
        return this;
    }

    private A4jBrainL turn(A4jBrainL.Direction dir) {
        if (dir == Direction.LEFT) {
            left(90);
        } else { // Direction.RIGHT
            right(90);
        }
        
        return this;
    }

    private A4jBrainL turn(A4jBrainL.Direction dir, long degrees) {
        if (dir == Direction.LEFT) {
            left(degrees);
        } else { // Direction.RIGHT
            right(degrees);
        }
        
        return this;
    }    

    @Override
    public A4jBrainL goHome() {
        //processRecordedMovements(recorder.home());
        return this;
    }
    
    @Override
    public A4jBrainL replay() {
        //processRecordedMovements(recorder.getRecording());
        return this;
    }
    
    @Override
    public A4jBrainL left(long degrees) {
        System.out.println("Turn left " + degrees + " degrees.");
        if (isRecording) {
            //recorder.recordAction(A4jBlackBox.Action.LEFT, speed);
        }

        // MAH: Add in speed/duration/bearing.
        // MAH: Add in direction/distance? (enh)
        controller.left(degrees);
        return this;
    }

    @Override
    public A4jBrainL right(long degrees) {
        System.out.println("Turn right " + degrees + " degrees.");
        if (isRecording) {
            //recorder.recordAction(A4jBlackBox.Action.RIGHT, speed);
        }

        controller.right(degrees);
        return this;
    }
    
    @Override
    public void processRecordedMovements(List<Movement> moves) {
        // Disable recording for playback
        isRecording = false;

        // MAH: Fix this to replay (after recording) distances/degrees.
        for (Movement curMov : moves) {
            switch(curMov.getAction()) {
                case FORWARD:
                    forward(0);
                    break;
                case BACKWARD:
                    backward(0);
                    break;
                case RIGHT:
                    right(0);
                    break;
                case LEFT:
                    left(0);
                    break;
                case STAY:
                    stay();
                    break;
            }
            hold(curMov.getDuration());
            System.out.println(curMov);
        }
            
        // Re-enable recording
        isRecording = true;    
    }    
}

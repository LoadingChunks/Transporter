/*
 * Copyright 2011 frdfsnlght <frdfsnlght@gmail.com>.
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
package org.bennedum.transporter.net;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Result {

    private State state = State.WAITING;
    private Message result = null;

    public void setResult(Message result) {
        this.result = result;
        this.state = State.COMPLETED;
        synchronized (this) {
            this.notifyAll();
        }
    }

    public void cancel() {
        synchronized (this) {
            if (state == State.COMPLETED) return;
            state = State.CANCELLED;
            this.notifyAll();
        }
    }

    public void timeout() {
        synchronized (this) {
            if (state == State.COMPLETED) return;
            state = State.TIMEOUT;
            this.notifyAll();
        }
    }

    public boolean isTimeout() {
        return state == State.TIMEOUT;
    }

    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    public boolean isWaiting() {
        return state == State.WAITING;
    }

    public Message getResult() {
        return result;
    }
    
    public Message get() throws InterruptedException, CancellationException, TimeoutException {
        return get(0);
    }

    public Message get(long time) throws InterruptedException, CancellationException, TimeoutException {
        synchronized (this) {
            if (state == State.COMPLETED) return result;
            if (state == State.TIMEOUT) throw new TimeoutException();
            if (state == State.CANCELLED) throw new CancellationException();
            this.wait(time);
            if (state == State.COMPLETED) return result;
            if (state == State.CANCELLED) throw new CancellationException();
            state = State.TIMEOUT;
            throw new TimeoutException();
        }
    }

    private enum State {
        WAITING,
        COMPLETED,
        CANCELLED,
        TIMEOUT;
    }

}

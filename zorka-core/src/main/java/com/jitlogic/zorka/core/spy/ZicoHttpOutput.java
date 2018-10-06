/*
 * Copyright (c) 2012-2018 Rafał Lewczuk All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jitlogic.zorka.core.spy;

import com.jitlogic.zorka.common.http.HttpRequest;
import com.jitlogic.zorka.common.http.HttpResponse;
import com.jitlogic.zorka.common.http.HttpUtil;
import com.jitlogic.zorka.common.tracedata.SymbolRegistry;
import com.jitlogic.zorka.common.tracedata.SymbolicRecord;
import com.jitlogic.zorka.common.util.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.jitlogic.zorka.common.util.ZorkaConfig.parseInt;
import static com.jitlogic.zorka.common.util.ZorkaConfig.parseStr;

public abstract class ZicoHttpOutput extends ZorkaAsyncThread<SymbolicRecord> {

    protected String agentUUID, authKey;
    protected String app, env, hostname;
    protected String sessionUUID, sessionKey;

    protected String submitTraceUrl, submitAgentUrl, registerUrl, sessionUrl;

    protected int retries, timeout;
    protected long retryTime, retryTimeExp;

    protected SymbolRegistry registry;

    protected ZorkaConfig config;

    protected abstract void resetState();


    public ZicoHttpOutput(ZorkaConfig config, Map<String,String> conf, SymbolRegistry registry) {
        super("ZORKA-CBOR-OUTPUT", parseInt(conf.get("http.qlen"), 64, "tracer.http.qlen"), 1);

        this.config = config;

        this.agentUUID = conf.get("agent.uuid");

        this.hostname = parseStr(conf.get("hostname"), null, null,
                "CborTraceOutput: missing mandatory parameter: tracer.hostname");

        this.app = parseStr(conf.get("app.name"), null, null,
                "CborTraceOutput: missing mandatory parameter: tracer.app.name");

        this.env = parseStr(conf.get("env.name"), null, null,
                "CborTraceOutput: missing mandatory parameter: tracer.env.name");

        this.authKey = conf.get("auth.key");
        this.sessionKey = conf.get("sessn.key");

        String url = parseStr(conf.get("http.url"), null, null,
                "CborTraceOutput: missing mandatory parameter: tracer.http.url");

        if (!url.endsWith("/")) url = url + "/";
        url += "agent/";

        this.submitAgentUrl = url + "submit/agd";
        this.submitTraceUrl = url + "submit/trc";

        this.registerUrl = url + "register";
        this.sessionUrl = url + "session";

        this.retries = parseInt(conf.get("http.retries"), 10, "tracer.http.retries");
        this.retryTime = parseInt(conf.get("http.retry.time"), 125, "tracer.http.retry.time");
        this.retryTimeExp = parseInt(conf.get("http.retry.exp"), 2, "tracer.http.retry.exp");
        this.timeout = parseInt(conf.get("http.timeout"), 60000, "tracer.http.output");
        this.registry = registry;
    }

    /**
     * Registers agent in collector. Obtains and saves agent UUID and agent key.
     */
    public void register() {
        resetState();

        String json = new JSONWriter()
                .write(ZorkaUtil.map(
                        "rkey", authKey,
                        "name", hostname,
                        "app", app,
                        "env", env
                ));

        log.info("Registering agent as: name=" + hostname + " app=" + app + " env=" + env);

        try {
            HttpRequest req = HttpUtil.POST(registerUrl, json)
                    .withHeader("Content-Type", "application/json");
            HttpResponse res = req.go();
            if (res.getStatus() == 201 || res.getStatus() == 200) {
                Object rslt = new JSONReader().read(res.getBodyAsString());
                if (rslt instanceof Map) {
                    Map m = (Map)rslt;
                    Object u = m.get("uuid");
                    if (u instanceof String) {
                        agentUUID = (String) m.get("uuid");
                        sessionKey = (String) m.get("authkey");
                        config.writeCfg("tracer.net.agent.uuid", agentUUID);
                        config.writeCfg("tracer.net.sessn.key", sessionKey);
                        log.info("Successfully registered agent with uuid=" + agentUUID);
                    } else {
                        throw new ZorkaRuntimeException("Invalid registration response from collector: missing or bad UUID '" + res.getBodyAsString() + "'");
                    }
                } else {
                    throw new ZorkaRuntimeException("Invalid registration response from collector: '" + res.getBodyAsString() + "'");
                }
            } else {
                throw new ZorkaRuntimeException("Invalid registration response from collector: "
                        + res.getStatus() + ": " + res.getStatusMsg());
            }
        } catch (IOException e) {
            throw new ZorkaRuntimeException("I/O error occured while registering agent", e);
        }
    }

    public String getAgentUUID() {
        return agentUUID;
    }


    public void openSession() {
        resetState();

        try {
            if (agentUUID == null) {
                log.info("Agent not registered (yet). Registering ...");
                register();
            }
        } catch (ZorkaRuntimeException e) {
            log.error("Error registering agent.", e);
            throw e;
        }

        try {
            log.debug("Requesting session for agent: uuid=" + agentUUID);
            HttpRequest req = HttpUtil.POST(sessionUrl,
                    new JSONWriter().write(ZorkaUtil.map(
                            "uuid", agentUUID,
                            "authkey", sessionKey)));
            HttpResponse res = req.go();
            if (res.getStatus() == 200) {
                Object rslt = new JSONReader().read(res.getBodyAsString());
                if (rslt instanceof Map) {
                    Map m = (Map)rslt;
                    Object s = m.get("session");
                    if (s instanceof String) {
                        sessionUUID = (String)s;
                        log.debug("Obtained session: uuid=" + sessionUUID);
                    } else {
                        throw new ZorkaRuntimeException("Invalid session response: '" + res.getBodyAsString() + "'");
                    }
                } else {
                    throw new ZorkaRuntimeException("Invalid session response: '" + res.getBodyAsString() + "'");
                }
            } else if (res.getStatus() == 401) {
                throw new ZorkaRuntimeException("Not authorized. Check trapper.cbor.auth-key property.");
            } else {
                throw new ZorkaRuntimeException("Server error: " + res.getStatus() + " " + res.getStatusMsg());
            }
        } catch (IOException e) {
            throw new ZorkaRuntimeException("I/O exception: " + e.getMessage(), e);
        }
    }

    public void newSession() {
        long rt = retryTime;
        for (int i = 0; i < retries+1; i++) {
            try {
                openSession();

                if (sessionUUID != null) {
                    break;
                }
            } catch (Exception e) {
                log.error("Cannot open collector session to: " + sessionUrl, e);
            }

            rt *= retryTimeExp;

            try {
                Thread.sleep(rt);
            } catch (InterruptedException e) {
            }
        }

        if (sessionUUID == null) {
            throw new ZorkaRuntimeException("Cannot obtain agent session.");
        }
    }

    protected void send(InputStream bodyStream, int bodyLength, String uri, String traceUUID) {
        try {
            HttpRequest req = HttpUtil.POST(uri, bodyStream, bodyLength);
            req.setHeader("X-Zorka-Agent-UUID", agentUUID);
            req.setHeader("X-Zorka-Session-UUID", sessionUUID);
            req.setHeader("Content-Type", "application/zorka+cbor+v1");
            if (traceUUID != null) req.setHeader("X-Zorka-Trace-UUID", traceUUID);
            HttpResponse res = req.go();
            if (res.getStatus() < 300) {
                log.trace("Submitted: " + uri + " : " + traceUUID);
            } else if (res.getStatus() == 412) {
                throw new ZorkaRuntimeException("Resend.");
            } else {
                throw new ZorkaRuntimeException("Server error: " + res.getStatus() + " " + res.getStatus());
            }
        } catch (IOException e) {
            throw new ZorkaRuntimeException("I/O exception: " + e.getMessage());
        }

    }

}

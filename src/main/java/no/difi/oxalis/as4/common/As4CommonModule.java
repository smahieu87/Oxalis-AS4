/*
 * Copyright 2010-2017 Norwegian Agency for Public Management and eGovernment (Difi)
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package no.difi.oxalis.as4.common;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.header.HeaderParser;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.config.As4Conf;
import no.difi.oxalis.as4.outbound.ActionProvider;
import no.difi.oxalis.as4.outbound.DefaultActionProvider;
import no.difi.oxalis.as4.util.As4MessageFactory;
import no.difi.oxalis.as4.util.PolicyService;
import no.difi.oxalis.as4.util.TransmissionRequestUtil;
import no.difi.oxalis.commons.guice.ImplLoader;
import no.difi.oxalis.commons.guice.OxalisModule;
import no.difi.vefa.peppol.mode.Mode;

import static no.difi.oxalis.as4.common.AS4Constants.*;

@Slf4j
public class As4CommonModule extends OxalisModule {

    @Override
    protected void configure() {
        bindTyped(MessageIdGenerator.class, DefaultMessageIdGenerator.class);
        bindTyped(HeaderParser.class, DummyHeaderParser.class);
        bind(As4MessageFactory.class);
        bindSettings(As4Conf.class);
        bind(MerlinProvider.class);
    }

    @Provides
    @Singleton
    public MessageIdGenerator getMessageIdGenerator(Injector injector, Settings<As4Conf> settings) {
        return ImplLoader.get(injector, MessageIdGenerator.class, settings, As4Conf.MSGID_GENERATOR);
    }

    @Provides
    @Singleton
    public PolicyService getPolicyService(Mode mode, Settings<As4Conf> settings, ActionProvider actionProvider) {
        String type = settings.getString(As4Conf.TYPE);

        if (Mode.PRODUCTION.equals(mode.getIdentifier()) && !PEPPOL.equals(type)) {
            throw new IllegalStateException("oxalis.as4.type has to be peppol in PRODUCTION!");
        }

        if (CEF_CONNECTIVITY.equalsIgnoreCase(type)) {
            return new PolicyService(actionProvider) {
                @Override
                protected String getDefaultPolicy() {
                    return "/eDeliveryAS4Policy.xml";
                }
            };
        } else if (CEF_CONFORMANCE.equalsIgnoreCase(type)) {
            return new PolicyService(actionProvider) {

                @Override
                protected String getPolicyClasspath(String action, String service) {
                    log.debug("Service = {}, Action = {}", service, action);

                    if ("SRV_ONEWAY_SIGNONLY".equals(service)
                            && "busdox-docid-qns::ACT_ONEWAY_SIGNONLY".equals(action)) {
                        return "/signOnly.xml";
                    }

                    return getDefaultPolicy();
                }


                @Override
                protected String getDefaultPolicy() {
                    return "/eDeliveryAS4Policy.xml";
                }
            };
        }

        return new PolicyService(actionProvider);
    }

    @Provides
    @Singleton
    public ActionProvider getActionProvider(Settings<As4Conf> settings) {
        String type = settings.getString(As4Conf.TYPE);
        if (CEF_CONNECTIVITY.equalsIgnoreCase(type)) {
            return p -> {
                String action = TransmissionRequestUtil.translateDocumentTypeToAction(p);

                if (action.startsWith("connectivity::cef##connectivity::")) {
                    return action.replaceFirst("connectivity::cef##connectivity::", "");
                }

                return action;
            };
        } else if (CEF_CONFORMANCE.equalsIgnoreCase(type)) {
            return p -> {
                if ("http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/test".equals(p.getIdentifier())) {
                    return "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/test";
                }

                return TransmissionRequestUtil.translateDocumentTypeToAction(p);
            };
        }

        return new DefaultActionProvider();
    }
}
/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.identity.oauth2.impersonation;

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth.internal.OAuthComponentServiceHolder;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.authz.OAuthAuthzReqMessageContext;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AuthorizeReqDTO;
import org.wso2.carbon.identity.oauth2.impersonation.models.ImpersonationContext;
import org.wso2.carbon.identity.oauth2.impersonation.models.ImpersonationRequestDTO;
import org.wso2.carbon.identity.oauth2.impersonation.validators.SubjectScopeValidator;
import org.wso2.carbon.identity.oauth2.internal.OAuth2ServiceComponentHolder;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.oauth2.validators.DefaultOAuth2ScopeValidator;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.wso2.carbon.identity.oauth2.impersonation.utils.Constants.OAUTH_2;

/**
 * Unit test cases for {@link SubjectScopeValidatorTest}
 */
@Listeners(MockitoTestNGListener.class)
public class SubjectScopeValidatorTest {

    @Mock
    private AuthenticatedUser impersonator;
    @Mock
    private OAuth2AuthorizeReqDTO oAuth2AuthorizeReqDTO;
    @Mock
    private OAuthAuthzReqMessageContext oAuthAuthzReqMessageContext;
    @Mock
    private DefaultOAuth2ScopeValidator defaultOAuth2ScopeValidator;
    @Mock
    private ApplicationManagementService applicationManagementService;
    @Mock
    private OAuthComponentServiceHolder mockOAuthComponentServiceHolder;
    @Mock
    private OAuthServerConfiguration mockOAuthServerConfiguration;
    @Mock
    private RealmService mockRealmService;
    @Mock
    private TenantManager mockTenantManager;
    private ImpersonationRequestDTO impersonationRequestDTO;
    private static final String[] SCOPES_WITHOUT_OPENID = new String[]{"scope1", "scope2"};
    private MockedStatic<OAuthComponentServiceHolder> oAuthComponentServiceHolder;
    private MockedStatic<OAuth2Util> mockedOAuth2Util;
    private MockedStatic<OAuth2ServiceComponentHolder> oAuth2ServiceComponentHolder;
    private MockedStatic<OAuthServerConfiguration> oAuthServerConfiguration;

    @BeforeMethod
    public void setUp() throws Exception {

        oAuthServerConfiguration = mockStatic(OAuthServerConfiguration.class);
        oAuthServerConfiguration.when(OAuthServerConfiguration::getInstance).thenReturn(mockOAuthServerConfiguration);
        oAuthComponentServiceHolder = mockStatic(OAuthComponentServiceHolder.class);
        oAuthComponentServiceHolder.when(OAuthComponentServiceHolder::getInstance)
                .thenReturn(mockOAuthComponentServiceHolder);
        lenient().when(mockOAuthComponentServiceHolder.getRealmService()).thenReturn(mockRealmService);
        lenient().when(mockRealmService.getTenantManager()).thenReturn(mockTenantManager);
        lenient().when(mockTenantManager.getTenantId("carbon.super")).thenReturn(-1234);
        mockedOAuth2Util = mockStatic(OAuth2Util.class);
        oAuth2ServiceComponentHolder = mockStatic(OAuth2ServiceComponentHolder.class);
        oAuth2ServiceComponentHolder.when(
                OAuth2ServiceComponentHolder::getApplicationMgtService).thenReturn(applicationManagementService);
        lenient().when(applicationManagementService.getApplicationResourceIDByInboundKey("dummyConsumerKey",
                OAUTH_2, "carbon.super")).thenReturn("dummyAppId");
        lenient().when(impersonator.getLoggableMaskedUserId()).thenReturn("123456789");
        lenient().when(oAuth2AuthorizeReqDTO.getRequestedSubjectId()).thenReturn("dummySubjectId");
        lenient().when(oAuth2AuthorizeReqDTO.getUser()).thenReturn(impersonator);
        lenient().when(oAuth2AuthorizeReqDTO.getConsumerKey()).thenReturn("dummyConsumerKey");
        lenient().when(oAuth2AuthorizeReqDTO.getScopes()).thenReturn(SCOPES_WITHOUT_OPENID);
        lenient().when(oAuth2AuthorizeReqDTO.getTenantDomain()).thenReturn("carbon.super");
        lenient().when(oAuthAuthzReqMessageContext.getAuthorizationReqDTO()).thenReturn(oAuth2AuthorizeReqDTO);
        lenient().when(oAuthAuthzReqMessageContext.getRequestedScopes()).thenReturn(SCOPES_WITHOUT_OPENID);
        impersonationRequestDTO = new ImpersonationRequestDTO();
        impersonationRequestDTO.setoAuthAuthzReqMessageContext(oAuthAuthzReqMessageContext);
        impersonationRequestDTO.setImpersonator(impersonator);
        impersonationRequestDTO.setSubject("dummySubjectId");
        lenient().when(impersonator.getTenantDomain()).thenReturn("carbon.super");
        lenient().when(impersonator.getUserStoreDomain()).thenReturn("PRIMARY");

        mockedOAuth2Util.when(() -> OAuth2Util.getAuthenticatedUser(
                "dummySubjectId", impersonator.getTenantDomain(), "dummyConsumerKey"))
                .thenReturn(getDummyAuthenticatedUserUser());
    }

    private AuthenticatedUser getDummyAuthenticatedUserUser() {

        AuthenticatedUser authenticatedImpersonatingUser = new AuthenticatedUser();
        authenticatedImpersonatingUser.setUserId("dummyUserId");
        authenticatedImpersonatingUser.setAuthenticatedSubjectIdentifier("dummySubjectId");
        authenticatedImpersonatingUser.setUserName("dummyUserName");
        authenticatedImpersonatingUser.setUserStoreDomain("dummyUserStore");
        authenticatedImpersonatingUser.setTenantDomain("carbon.super");
        return authenticatedImpersonatingUser;
    }

    @AfterMethod
    public void tearDown() {
        oAuth2ServiceComponentHolder.close();
        oAuthComponentServiceHolder.close();;
        oAuthServerConfiguration.close();
        mockedOAuth2Util.close();
    }

    @Test
    public void testValidateImpersonation() throws IdentityException, NoSuchFieldException, IllegalAccessException {

        when(defaultOAuth2ScopeValidator.validateScope(oAuthAuthzReqMessageContext)).thenReturn(
                Arrays.asList("scope1", "scope2"));

        ImpersonationContext impersonationContext = new ImpersonationContext();
        impersonationContext.setImpersonationRequestDTO(impersonationRequestDTO);
        SubjectScopeValidator subjectScopeValidator = new SubjectScopeValidator();
        Field field = SubjectScopeValidator.class.getDeclaredField("scopeValidator");
        field.setAccessible(true);
        field.set(subjectScopeValidator, defaultOAuth2ScopeValidator);

        impersonationContext =
                subjectScopeValidator.validateImpersonation(impersonationContext);

        assertTrue(impersonationContext.isValidated(), "Impersonation context's validated attribute should be true");
        assertNull(impersonationContext.getValidationFailureErrorMessage(),
                "Validation error message should be null");
    }

    @Test
    public void testValidateImpersonationNegativeCase() throws IdentityException, NoSuchFieldException,
            IllegalAccessException {

        when(defaultOAuth2ScopeValidator.validateScope(oAuthAuthzReqMessageContext)).thenThrow
                (IdentityOAuth2Exception.class);

        ImpersonationContext impersonationContext = new ImpersonationContext();
        impersonationContext.setImpersonationRequestDTO(impersonationRequestDTO);
        SubjectScopeValidator subjectScopeValidator = new SubjectScopeValidator();
        Field field = SubjectScopeValidator.class.getDeclaredField("scopeValidator");
        field.setAccessible(true);
        field.set(subjectScopeValidator, defaultOAuth2ScopeValidator);

        try {
            impersonationContext =
                    subjectScopeValidator.validateImpersonation(impersonationContext);
        } catch (IdentityOAuth2Exception e) {

            assertFalse(impersonationContext.isValidated(), "Impersonation context's validated" +
                    "attribute should be false");
        }
    }
}

// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertTrue;

/**
 * Multiple clusters in a domain tests.
 *
 * <p>More than 1 cluster is created in a domain , like configured and dynamic
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItMultipleClusters extends BaseTest {

  private static final String TWO_CONFIGURED_CLUSTER_SCRIPT =
      "create-domain-two-configured-cluster.py";
  private static final String TWO_MIXED_CLUSTER_SCRIPT = "create-domain-two-mixed-cluster.py";
  private static final String DOMAINUID = "twoconfigclustdomain";
  private static Operator operator1;
  private static String customDomainTemplate;
  private static String testClassName ;
  private static String domainNS1 ;
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      testClassName = new Object() {}.getClass().getEnclosingClass().getSimpleName();
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE, testClassName);
      String template =
          BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
      String add =
          "  - clusterName: %CLUSTER_NAME%-2\n"
              + "    serverStartState: \"RUNNING\"\n"
              + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
      customDomainTemplate = BaseTest.getResultDir() + "/customDomainTemplate.yaml";
      Files.copy(
          Paths.get(template),
          Paths.get(customDomainTemplate),
          StandardCopyOption.REPLACE_EXISTING);
      Files.write(Paths.get(customDomainTemplate), add.getBytes(), StandardOpenOption.APPEND);
      
      // create operator1
      if(operator1 == null ) {
        Map<String, Object> operatorMap = TestUtils.createOperatorMap(getNewNumber(), true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assert.assertNotNull(operator1);
        domainNS1 = ((ArrayList<String>)operatorMap.get("domainNamespaces")).get(0);
      }
      
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      LoggerHelper.getLocal().log(Level.INFO, "+++++++++++++++++++++++++++++++++---------------------------------+");
      LoggerHelper.getLocal().log(Level.INFO, "BEGIN");
      LoggerHelper.getLocal().log(Level.INFO, "Run once, release cluster lease");
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
    }
  }

  /**
   * Create 2 configured clusters in a domain each having 2 managed servers. Verify the managed
   * servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoConfiguredCluster() throws Exception {
    Assume.assumeTrue(FULLTEST);

    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("domainUID", DOMAINUID);
      domainMap.put("clusterType", "CONFIGURED");
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/"
              + TWO_CONFIGURED_CLUSTER_SCRIPT);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server",
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-new-managed-server1",
        DOMAINUID + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);
      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 clusters(configured and dynamic) in a domain each having 2 managed servers. Verify the
   * managed servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoMixedCluster() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String domainuid = "twomixedclusterdomain";
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String template =
        BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("domainUID", domainuid);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/" + TWO_MIXED_CLUSTER_SCRIPT);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
        domainuid + "-" + domain.getAdminServerName(),
        domainuid + "-managed-server",
        domainuid + "-managed-server1",
        domainuid + "-managed-server2",
        domainuid + "-new-managed-server1",
        domainuid + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);

      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 dynamic clusters in a domain using WDT in inimage each having 2 managed servers.
   * Verify the managed servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoClusterWdtInImage() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String domainuid = "twoclusterdomainwdt";
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainInImageMap(getNewNumber(), true, testClassName);
      domainMap.put("domainUID", domainuid);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "customWdtTemplate",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/multipleclusters/wdtmultipledynclusters.yml");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
        domainuid + "-" + domain.getAdminServerName(),
        domainuid + "-managed-server1",
        domainuid + "-managed-server2",
        domainuid + "-managed-server-21",
        domainuid + "-managed-server-22",
      };
      verifyServersStatus(domain, pods);
      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Verifies all of the servers in the cluster are in Running status.
   *
   * @param domain Domain
   * @param pods array pod names to check the status for
   */
  private void verifyServersStatus(Domain domain, String[] pods) {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", DOMAINUID);
    String namespace = domain.getDomainNs();
    for (String pod : pods) {
      assertTrue(
          pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
    }
  }
}
package me.paulbares.spring.conf;

import me.paulbares.camunda.ApprovalWorkflowTaskListener;
import me.paulbares.camunda.BasicApprovalWorflow;
import me.paulbares.service.NotificationService;
import me.paulbares.subscription.ApproverWorkflowRegistrar;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Collections;

/**
 * Configuration for {@link org.camunda.bpm.engine.ProcessEngine}.
 */
@Configuration
public class CamundaProcessEngineConfiguration {

  /**
   * The {@link DataSource}.
   */
  @Autowired
  public DataSource dataSource;

  /**
   * The {@link ApproverWorkflowRegistrar} to inject into {@link ApprovalWorkflowTaskListener}.
   */
  @Autowired
  public ApproverWorkflowRegistrar registrar;

  @Autowired
  public NotificationService notificationService;

  /**
   * The actual {@link ProcessEngineConfigurationImpl} to be used by the workflow engine.
   *
   * @return The actual {@link ProcessEngineConfigurationImpl} to be used by the workflow engine.
   */
  @Bean
  public ProcessEngineConfigurationImpl processEngineConfiguration() {
    ProcessEngineConfigurationImpl conf = new StandaloneProcessEngineConfiguration()
            .setDataSource(this.dataSource)
            .setDatabaseSchemaUpdate("true");
    conf.setBeans(Collections.singletonMap(BasicApprovalWorflow.LISTENER_BEAN_NAME,
            new ApprovalWorkflowTaskListener(this.notificationService, this.registrar)));
    return conf;
  }

  /**
   * Returns the process engine.
   *
   * @return the process engine.
   */
  @Bean
  public ProcessEngineFactoryBean processEngine() {
    ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
    factoryBean.setProcessEngineConfiguration(processEngineConfiguration());
    return factoryBean;
  }
}

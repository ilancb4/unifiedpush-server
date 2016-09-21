package org.jboss.aerogear.unifiedpush.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.jboss.aerogear.unifiedpush.spring.utils.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Startup
public class Configuration {

	private final Logger logger = LoggerFactory.getLogger(Configuration.class);

	public static final String PROPERTIES_FILE_KEY = "aerogear.config";
	public static final String PROPERTIES_DOCUMENTS_KEY = "aerogear.config.document.path.root";
	public static final String PROP_ENABLE_VERIFICATION = "aerogear.config.verification.enable_verification";
	public static final String PROP_MASTER_VERIFICATION = "aerogear.config.verification.master_code";

	// DONOT expose this property as public.
	private static final String PROP_OAUTH2_ENABLE = "aerogear.config.oauth2.enable";

	public static final String PROP_OAUTH2_USERNAME = "aerogear.config.oauth2.username";
	public static final String PROP_OAUTH2_PASSWORD = "aerogear.config.oauth2.password";

	public static final String PROP_OAUTH2_KEYCLOAK_PATH = "aerogear.config.oauth2.keycloak.path";
	public static final String PROP_OAUTH2_REALM_ID = "aerogear.config.oauth2.realm_id";
	public static final String PROP_OAUTH2_CLIENT_ID = "aerogear.config.oauth2.client_id";
	public static final String PROP_OAUTH2_DOMAIN = "aerogear.config.oauth2.webapp.domain";
	public static final String PROP_OAUTH2_PROTOCOL = "aerogear.config.oauth2.webapp.protocol";

	private Properties properties;
	private PropertyPlaceholderConfigurer configurer;

	@PostConstruct
	public void loadProperties() {
		final String propertiesFilePath = System.getProperty(PROPERTIES_FILE_KEY);
		properties = new Properties();
		configurer = new PropertyPlaceholderConfigurer();
		InputStream is = null;

		if (propertiesFilePath != null) {
			try {
				is = ResourceUtils.getURL(propertiesFilePath).openStream();

				loadPropertiesFromStream(is);
			} catch (IOException e) {
				logger.error("cannot open file " + propertiesFilePath);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						// Nothig  we can do.
					}
				}
			}
		} else {
			loadDefaultProperties();
		}

		logger.info("Using runtime properties: " + properties.toString());
	}

	private void loadDefaultProperties() {
		InputStream inp = getClass().getClassLoader().getResourceAsStream("default.properties");
		if (inp == null) {
			logger.warn("default properties not found");
		} else {
			logger.warn("loading default properties from class path resource default.properties!");
			logger.warn("use -Daerogear.config=/PATH inorder to prevent this warning!");
			loadPropertiesFromStream(inp);
		}
	}

	private void loadPropertiesFromStream(InputStream inp) {
		try (InputStream in = inp) {
			properties.load(in);
		} catch (IOException e) {
			logger.error("Cannot load configuration", e);
		}
	}

	public String getProperty(String key) {
		return configurer.resolvePlaceholder(key, properties);
	}

	public boolean getProperty(String key, boolean defaultValue) {
		String value = getProperty(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.valueOf(value);
	}

	public Properties getProperties() {
		return new PropertiesView(properties, configurer);
	}

	public void setSystemPropertiesMode(int systemPropertiesMode) {
		this.configurer.setSystemPropertiesMode(systemPropertiesMode);
	}

	@SuppressWarnings("serial")
	private class PropertiesView extends Properties {
		private final Properties properties;
		private final PropertyPlaceholderConfigurer configurer;

		public PropertiesView(Properties properties, PropertyPlaceholderConfigurer configurer) {
			this.properties = properties;
			this.configurer = configurer;
		}

		public String getProperty(String key) {
			return configurer.resolvePlaceholder(key, properties);
		}
	}

	public boolean isOauth2Enabled(){
		return getProperty(Configuration.PROP_OAUTH2_ENABLE, false);
	}

}

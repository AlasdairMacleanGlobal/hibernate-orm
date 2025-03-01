/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import jakarta.persistence.TemporalType;

import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.ZonedDateTimeComparator;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Java type descriptor for the {@link ZonedDateTime} type.
 *
 * @author Steve Ebersole
 */
public class ZonedDateTimeJavaType extends AbstractTemporalJavaType<ZonedDateTime> implements VersionJavaType<ZonedDateTime> {
	/**
	 * Singleton access
	 */
	public static final ZonedDateTimeJavaType INSTANCE = new ZonedDateTimeJavaType();

	public ZonedDateTimeJavaType() {
		super( ZonedDateTime.class, ImmutableMutabilityPlan.instance(), ZonedDateTimeComparator.INSTANCE );
	}

	@Override
	public TemporalType getPrecision() {
		return TemporalType.TIMESTAMP;
	}

	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeIndicators stdIndicators) {
		final TemporalType temporalPrecision = stdIndicators.getTemporalPrecision();
		final JdbcTypeRegistry jdbcTypeRegistry = stdIndicators.getTypeConfiguration()
				.getJdbcTypeRegistry();
		if ( temporalPrecision == null || temporalPrecision == TemporalType.TIMESTAMP ) {
			switch ( stdIndicators.getDefaultTimeZoneStorageStrategy() ) {
				case NORMALIZE:
					return jdbcTypeRegistry.getDescriptor( Types.TIMESTAMP );
				case NORMALIZE_UTC:
					return jdbcTypeRegistry.getDescriptor( SqlTypes.TIMESTAMP_UTC );
				default:
					return jdbcTypeRegistry.getDescriptor( Types.TIMESTAMP_WITH_TIMEZONE );
			}
		}

		switch ( temporalPrecision ) {
			case TIME: {
				return jdbcTypeRegistry.getDescriptor( Types.TIME );
			}
			case DATE: {
				return jdbcTypeRegistry.getDescriptor( Types.DATE );
			}
			default: {
				throw new IllegalArgumentException( "Unexpected jakarta.persistence.TemporalType : " + temporalPrecision );
			}
		}
	}

	@Override
	protected <X> TemporalJavaType<X> forTimestampPrecision(TypeConfiguration typeConfiguration) {
		//noinspection unchecked
		return (TemporalJavaType<X>) this;
	}

	@Override
	public String toString(ZonedDateTime value) {
		return DateTimeFormatter.ISO_ZONED_DATE_TIME.format( value );
	}

	@Override
	public ZonedDateTime fromString(CharSequence string) {
		return ZonedDateTime.from( DateTimeFormatter.ISO_ZONED_DATE_TIME.parse( string ) );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <X> X unwrap(ZonedDateTime zonedDateTime, Class<X> type, WrapperOptions options) {
		if ( zonedDateTime == null ) {
			return null;
		}

		if ( ZonedDateTime.class.isAssignableFrom( type ) ) {
			return (X) zonedDateTime;
		}

		if ( OffsetDateTime.class.isAssignableFrom( type ) ) {
			return (X) OffsetDateTime.of( zonedDateTime.toLocalDateTime(), zonedDateTime.getOffset() );
		}

		if ( Calendar.class.isAssignableFrom( type ) ) {
			return (X) GregorianCalendar.from( zonedDateTime );
		}

		if ( Timestamp.class.isAssignableFrom( type ) ) {
			/*
			 * This works around two bugs:
			 * - HHH-13266 (JDK-8061577): around and before 1900,
			 * the number of milliseconds since the epoch does not mean the same thing
			 * for java.util and java.time, so conversion must be done using the year, month, day, hour, etc.
			 * - HHH-13379 (JDK-4312621): after 1908 (approximately),
			 * Daylight Saving Time introduces ambiguity in the year/month/day/hour/etc representation once a year
			 * (on DST end), so conversion must be done using the number of milliseconds since the epoch.
			 * - around 1905, both methods are equally valid, so we don't really care which one is used.
			 */
			if ( zonedDateTime.getYear() < 1905 ) {
				return (X) Timestamp.valueOf(
						zonedDateTime.withZoneSameInstant( ZoneId.systemDefault() ).toLocalDateTime()
				);
			}
			else {
				return (X) Timestamp.from( zonedDateTime.toInstant() );
			}
		}

		if ( java.sql.Date.class.isAssignableFrom( type ) ) {
			return (X) java.sql.Date.from( zonedDateTime.toInstant() );
		}

		if ( java.sql.Time.class.isAssignableFrom( type ) ) {
			return (X) java.sql.Time.from( zonedDateTime.toInstant() );
		}

		if ( Date.class.isAssignableFrom( type ) ) {
			return (X) Date.from( zonedDateTime.toInstant() );
		}

		if ( Long.class.isAssignableFrom( type ) ) {
			return (X) Long.valueOf( zonedDateTime.toInstant().toEpochMilli() );
		}

		throw unknownUnwrap( type );
	}

	@Override
	public <X> ZonedDateTime wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if (value instanceof ZonedDateTime) {
			return (ZonedDateTime) value;
		}

		if (value instanceof OffsetDateTime) {
			OffsetDateTime offsetDateTime = (OffsetDateTime) value;
			return offsetDateTime.toZonedDateTime();
		}

		if (value instanceof Timestamp) {
			final Timestamp ts = (Timestamp) value;
			/*
			 * This works around two bugs:
			 * - HHH-13266 (JDK-8061577): around and before 1900,
			 * the number of milliseconds since the epoch does not mean the same thing
			 * for java.util and java.time, so conversion must be done using the year, month, day, hour, etc.
			 * - HHH-13379 (JDK-4312621): after 1908 (approximately),
			 * Daylight Saving Time introduces ambiguity in the year/month/day/hour/etc representation once a year
			 * (on DST end), so conversion must be done using the number of milliseconds since the epoch.
			 * - around 1905, both methods are equally valid, so we don't really care which one is used.
			 */
			if ( ts.getYear() < 5 ) { // Timestamp year 0 is 1900
				return ts.toLocalDateTime().atZone( ZoneId.systemDefault() );
			}
			else {
				return ts.toInstant().atZone( ZoneId.systemDefault() );
			}
		}

		if (value instanceof Date) {
			final Date date = (Date) value;
			return ZonedDateTime.ofInstant( date.toInstant(), ZoneId.systemDefault() );
		}

		if (value instanceof Long) {
			return ZonedDateTime.ofInstant( Instant.ofEpochMilli( (Long) value ), ZoneId.systemDefault() );
		}

		if (value instanceof Calendar) {
			final Calendar calendar = (Calendar) value;
			return ZonedDateTime.ofInstant( calendar.toInstant(), calendar.getTimeZone().toZoneId() );
		}

		throw unknownWrap( value.getClass() );
	}

	@Override
	public int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
		return dialect.getDefaultTimestampPrecision();
	}

	@Override
	public ZonedDateTime seed(Long length, Integer precision, Integer scale, SharedSessionContractImplementor session) {
		return ZonedDateTime.now( ClockHelper.forPrecision( precision, session ) );
	}

	@Override
	public ZonedDateTime next(
			ZonedDateTime current,
			Long length,
			Integer precision,
			Integer scale,
			SharedSessionContractImplementor session) {
		return ZonedDateTime.now( ClockHelper.forPrecision( precision, session ) );
	}
}

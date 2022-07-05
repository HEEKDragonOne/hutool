package cn.hutool.core.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.impl.ArrayConverter;
import cn.hutool.core.convert.impl.BeanConverter;
import cn.hutool.core.convert.impl.CollectionConverter;
import cn.hutool.core.convert.impl.EnumConverter;
import cn.hutool.core.convert.impl.MapConverter;
import cn.hutool.core.convert.impl.NumberConverter;
import cn.hutool.core.convert.impl.PrimitiveConverter;
import cn.hutool.core.reflect.TypeReference;
import cn.hutool.core.reflect.TypeUtil;
import cn.hutool.core.util.ObjUtil;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * 复合转换器，融合了所有支持类型和自定义类型的转换规则
 * <p>
 * 将各种类型Convert对象放入符合转换器，通过convert方法查找目标类型对应的转换器，将被转换对象转换之。
 * </p>
 * <p>
 * 在此类中，存放着默认转换器和自定义转换器，默认转换器是Hutool中预定义的一些转换器，自定义转换器存放用户自定的转换器。
 * </p>
 *
 * @author Looly
 */
public class CompositeConverter extends RegisterConverter {
	private static final long serialVersionUID = 1L;

	/**
	 * 类级的内部类，也就是静态的成员式内部类，该内部类的实例与外部类的实例 没有绑定关系，而且只有被调用到才会装载，从而实现了延迟加载
	 */
	private static class SingletonHolder {
		/**
		 * 静态初始化器，由JVM来保证线程安全
		 */
		private static final CompositeConverter INSTANCE = new CompositeConverter();
	}

	/**
	 * 获得单例的 ConverterRegistry
	 *
	 * @return ConverterRegistry
	 */
	public static CompositeConverter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	/**
	 * 构造
	 */
	public CompositeConverter() {
		super();
	}

	/**
	 * 转换值为指定类型
	 *
	 * @param type  类型
	 * @param value 值
	 * @return 转换后的值，默认为{@code null}
	 * @throws ConvertException 转换器不存在
	 */
	@Override
	public Object convert(final Type type, final Object value) throws ConvertException {
		return convert(type, value, null);
	}

	/**
	 * 转换值为指定类型<br>
	 * 自定义转换器优先
	 *
	 * @param <T>          转换的目标类型（转换器转换到的类型）
	 * @param type         类型
	 * @param value        值
	 * @param defaultValue 默认值
	 * @return 转换后的值
	 * @throws ConvertException 转换器不存在
	 */
	@Override
	public <T> T convert(final Type type, final Object value, final T defaultValue) throws ConvertException {
		return convert(type, value, defaultValue, true);
	}

	/**
	 * 转换值为指定类型
	 *
	 * @param <T>           转换的目标类型（转换器转换到的类型）
	 * @param type          类型目标
	 * @param value         被转换值
	 * @param defaultValue  默认值
	 * @param isCustomFirst 是否自定义转换器优先
	 * @return 转换后的值
	 * @throws ConvertException 转换器不存在
	 */
	@SuppressWarnings("unchecked")
	public <T> T convert(Type type, final Object value, final T defaultValue, final boolean isCustomFirst) throws ConvertException {
		if (TypeUtil.isUnknown(type) && null == defaultValue) {
			// 对于用户不指定目标类型的情况，返回原值
			return (T) value;
		}
		if (ObjUtil.isNull(value)) {
			return defaultValue;
		}
		if (TypeUtil.isUnknown(type)) {
			if(null == defaultValue){
				throw new ConvertException("Unsupported convert to unKnow type: {}", type);
			}
			type = defaultValue.getClass();
		}

		if (type instanceof TypeReference) {
			type = ((TypeReference<?>) type).getType();
		}

		// 标准转换器
		final Converter converter = getConverter(type, isCustomFirst);
		if (null != converter) {
			return converter.convert(type, value, defaultValue);
		}

		Class<T> rowType = (Class<T>) TypeUtil.getClass(type);
		if (null == rowType) {
			if (null != defaultValue) {
				rowType = (Class<T>) defaultValue.getClass();
			} else {
				throw new ConvertException("Can not get class from type: {}", type);
			}
		}

		// 特殊类型转换，包括Collection、Map、强转、Array等
		final T result = convertSpecial(type, rowType, value, defaultValue);
		if (null != result) {
			return result;
		}

		// 尝试转Bean
		if (BeanUtil.isBean(rowType)) {
			return (T) BeanConverter.INSTANCE.convert(type, value);
		}

		// 无法转换
		throw new ConvertException("Can not convert from {}: [{}] to [{}]", value.getClass().getName(), value, type.getTypeName());
	}

	// ----------------------------------------------------------- Private method start

	/**
	 * 特殊类型转换<br>
	 * 包括：
	 *
	 * <pre>
	 * Collection
	 * Map
	 * 强转（无需转换）
	 * 数组
	 * </pre>
	 *
	 * @param <T>          转换的目标类型（转换器转换到的类型）
	 * @param type         类型
	 * @param value        值
	 * @param defaultValue 默认值
	 * @return 转换后的值
	 */
	@SuppressWarnings("unchecked")
	private <T> T convertSpecial(final Type type, final Class<T> rowType, final Object value, final T defaultValue) {
		if (null == rowType) {
			return null;
		}

		// 集合转换（含有泛型参数，不可以默认强转）
		if (Collection.class.isAssignableFrom(rowType)) {
			return (T) CollectionConverter.INSTANCE.convert(type, value, (Collection<?>) defaultValue);
		}

		// Map类型（含有泛型参数，不可以默认强转）
		if (Map.class.isAssignableFrom(rowType)) {
			return (T) MapConverter.INSTANCE.convert(type, value, (Map<?, ?>) defaultValue);
		}

		// 默认强转
		if (rowType.isInstance(value)) {
			return (T) value;
		}

		// 原始类型转换
		if(rowType.isPrimitive()){
			return PrimitiveConverter.INSTANCE.convert(type, value, defaultValue);
		}

		// 数字类型转换
		if(Number.class.isAssignableFrom(rowType)){
			return NumberConverter.INSTANCE.convert(type, value, defaultValue);
		}

		// 枚举转换
		if (rowType.isEnum()) {
			return EnumConverter.INSTANCE.convert(type, value, defaultValue);
		}

		// 数组转换
		if (rowType.isArray()) {
			return ArrayConverter.INSTANCE.convert(type, value, defaultValue);
		}

		// 表示非需要特殊转换的对象
		return null;
	}
	// ----------------------------------------------------------- Private method end
}
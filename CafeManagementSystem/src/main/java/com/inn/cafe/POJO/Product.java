package com.inn.cafe.POJO;

import java.io.Serializable;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.Data;

@NamedQuery(name = "Product.getAllProduct",query="select new com.inn.cafe.Wrapper.ProductWrapper(p.id,p.name,p.description,p.price,p.status,p.category.id,p.category.name)from product p")

@NamedQuery(name = "Product.updateProductStatus",query="update product p set p.status=:status where p.id=:id")

@NamedQuery(name="product.getProductByCategory",query="select new com.inn.cafe.Wrapper.ProductWrapper(p.id,p.name)from Product p where p.category.id=:id and p.status='true'")

@NamedQuery(name="product.getProductById",query="select new com.inn.cafe.Wrapper.ProductWrapper(p.id,p.name,p.description,p.price) from product p where p.id=:id")


@Data
@Entity
@DynamicInsert
@DynamicUpdate
@Table
public class Product implements Serializable{

	public static final Long SerialVersionUid=123456L;
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;
	
	@Column(name="name")
	private String name;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="category_fk",nullable=false)
	private Category category;
	
	@Column(name="description")
	private String description;
	
	@Column(name="price")
	private Integer price;
	
	@Column(name="status")
	private String status;
	
	
	
}
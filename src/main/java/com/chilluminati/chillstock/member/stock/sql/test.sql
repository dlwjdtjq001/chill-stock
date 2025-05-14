use chillstockDB;

desc user_tbl;
select * from user_tbl;

select * from user_table;

select * from product;

select b.business_id from business_table b join user_table u on b.user_id = u.user_id where b.user_id = 1;

select distinct s.stock_id, p.product_name, p.expiration_date, s.stock_amount, i.inbound_date
from product p
         join stock_table s on p.product_id = s.product_id
         join inbound_table i on i.product_id = p.product_id
         join business_table b on p.business_id = b.business_id
where b.business_id = (select b.business_id from business_table b join user_table u on b.user_id = u.user_id where u.user_id = 1) -- 로그인 한 사용자의 user_id
order by s.stock_id;

select distinct s.stock_id, p.product_id, p.product_name, p.expiration_date, s.stock_amount, i.inbound_date
from product p
         join stock_table s on p.product_id = s.product_id
         join inbound_table i on i.product_id = p.product_id
         join business_table b on p.business_id = b.business_id
where b.user_id = 2
order by s.stock_id;

select * from inbound_table;
select * from user_table;
select * from product;

select distinct s.stock_id, p.product_id, p.product_name, p.expiration_date, s.stock_amount, i.inbound_date
from product p
         join stock_table s on p.product_id = s.product_id
         join inbound_table i on i.product_id = p.product_id
         join business_table b on p.business_id = b.business_id
where i.inbound_status = '대기'
order by s.stock_id;

-- inbound_date is not null
select distinct s.stock_id, p.product_id, p.product_name, p.expiration_date, s.stock_amount, i.inbound_date
from stock_table s
    join product p on s.product_id = p.product_id
    join inbound_table i on i.product_id = p.product_id
    join business_table b on b.business_id = p.business_id
where b.user_id = 2 and i.inbound_date is not null;



select * from stock_table;
insert into stock_table (stock_amount, product_id, area_id) (select stock_amount, product_id, area_id from stock_table);

select * from outbound_table;
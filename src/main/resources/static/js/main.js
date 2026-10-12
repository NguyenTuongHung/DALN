document.querySelectorAll('.sport-pill').forEach(p=>{
    p.addEventListener('click',()=>{
      document.querySelectorAll('.sport-pill').forEach(x=>x.classList.remove('active'));
      p.classList.add('active');
    });
  });
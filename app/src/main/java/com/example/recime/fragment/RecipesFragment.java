package com.example.recime.fragment;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.activity.MainActivity;
import com.example.recime.adapter.RecipeAdapter;
import com.example.recime.helper.*;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
import java.util.*;

public class RecipesFragment extends Fragment {
  private View root;
  private LabViewModel vm;
  private final List<Recipe> shown = new ArrayList<>();
  private final List<String> books = new ArrayList<>();
  private final Map<String,Set<Long>> members = new HashMap<>();
  private List<Recipe> all = new ArrayList<>();
  private RecipeAdapter recipes;
  private BaseAdapter bookAdapter;
  private boolean showBooks = true;
  private String query = "", ingredient = "";
  private boolean favorite;
  public RecipesFragment() { super(); }
  public static RecipesFragment create(boolean discover) {
    RecipesFragment f = new RecipesFragment(); Bundle b = new Bundle();
    b.putBoolean("discover",discover); f.setArguments(b); return f;
  }
  public static RecipesFragment cookbook(String name) {
    RecipesFragment f=create(false); f.requireArguments().putString("book",name); return f;
  }
  private boolean discover() { return requireArguments().getBoolean("discover"); }
  private String book() { return requireArguments().getString("book", ""); }
  @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
    return inflater.cloneInContext(new ContextThemeWrapper(requireContext(),R.style.Theme_RecipeEditor))
        .inflate(R.layout.fragment_recipes,parent,false);
  }
  @Override public void onViewCreated(View v,Bundle saved) {
    root=v; vm=new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    if(saved!=null) { showBooks=saved.getBoolean("showBooks",true); query=saved.getString("query","");
      ingredient=saved.getString("ingredient",""); favorite=saved.getBoolean("favorite"); }
    Ui.set(v,R.id.heading,discover()?"Discover":book().isEmpty()?"Recipes":book());
    v.findViewById(R.id.catalog_notice).setVisibility(discover()?View.VISIBLE:View.GONE);
    v.findViewById(R.id.library_sections).setVisibility(discover()||!book().isEmpty()?View.GONE:View.VISIBLE);
    v.findViewById(R.id.cookbook_options).setVisibility(book().isEmpty()?View.GONE:View.VISIBLE);
    v.findViewById(R.id.cookbook_options).setOnClickListener(x->bookActions());
    v.findViewById(R.id.new_book).setOnClickListener(x->CookbookDialog.create("create","").show(getParentFragmentManager(),"book"));
    v.findViewById(R.id.manage_books).setOnClickListener(x->{showBooks=true;((EditText)v.findViewById(R.id.search)).setText("");render();});
    v.findViewById(R.id.all_recipes).setOnClickListener(x->{showBooks=false;render();});
    v.findViewById(R.id.add).setOnClickListener(x->new com.google.android.material.dialog.MaterialAlertDialogBuilder(v.getContext())
        .setTitle("Thêm công thức").setItems(new String[]{"Viết công thức","Nhập link, ảnh, văn bản / AI"},(d,index)->{
          MainActivity a=(MainActivity)requireActivity();
          if(index==0)a.open(EditorFragment.create(0,null,"","")); else a.openImport();
        }).show());
    recipes=new RecipeAdapter(v.getContext(),R.layout.item_library_recipe,shown);
    bookAdapter=new BaseAdapter() {
      public int getCount(){return books.size();} public String getItem(int p){return books.get(p);}
      public long getItemId(int p){return p;}
      public View getView(int p,View recycled,ViewGroup parent){
        View card=recycled==null?LayoutInflater.from(v.getContext()).inflate(R.layout.item_cookbook,parent,false):recycled;
        String name=getItem(p); ((TextView)card.findViewById(R.id.title)).setText(name);
        int count=0; for(Recipe r:all)if(LibraryScope.includes(r,false)&&members.getOrDefault(name,Collections.emptySet()).contains(r.id))count++;
        ((TextView)card.findViewById(R.id.meta)).setText(count+" công thức"); return card;
      }
    };
    GridView grid=v.findViewById(R.id.list);
    grid.setOnItemClickListener((p,cell,pos,id)->{
      MainActivity a=(MainActivity)requireActivity();
      if(grid.getAdapter()==bookAdapter)a.open(cookbook(books.get(pos)));
      else a.open(DetailFragment.create(shown.get(pos).id));
    });
    ((EditText)v.findViewById(R.id.search)).setText(query);
    ((EditText)v.findViewById(R.id.ingredient_filter)).setText(ingredient);
    ((CheckBox)v.findViewById(R.id.favorites)).setChecked(favorite);
    TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}
      public void afterTextChanged(Editable e){} public void onTextChanged(CharSequence s,int a,int b,int c){render();}};
    ((EditText)v.findViewById(R.id.search)).addTextChangedListener(watcher);
    ((EditText)v.findViewById(R.id.ingredient_filter)).addTextChangedListener(watcher);
    ((CheckBox)v.findViewById(R.id.favorites)).setOnCheckedChangeListener((b,c)->render());
    vm.recipes.observe(getViewLifecycleOwner(),items->{all=items;refreshBooks();});
    vm.cookbooks.observe(getViewLifecycleOwner(),names->refreshBooks());
    vm.repository.ready.observe(getViewLifecycleOwner(),ready->render());
    getParentFragmentManager().setFragmentResultListener("cookbook_changed",getViewLifecycleOwner(),(key,result)->{
      if(!book().equals(result.getString("old")))return;
      if(result.getString("action").equals("rename")){
        requireArguments().putString("book",result.getString("name"));Ui.set(root,R.id.heading,book());
        root.findViewById(R.id.cookbook_options).setEnabled(true);refreshBooks();
      }else if(result.getString("action").equals("delete"))getParentFragmentManager().popBackStack();
    });
    render();
  }
  private void refreshBooks(){
    books.clear(); members.clear();
    for(String name:vm.repository.dao.bookNamesSnapshot()){
      Set<Long> ids=vm.repository.dao.recipeIdsForCookbook(name);
      boolean personal=ids.isEmpty();
      for(Recipe r:all)if(LibraryScope.includes(r,false)&&ids.contains(r.id))personal=true;
      if(personal){books.add(name);members.put(name,ids);}
    }
    render();
  }
  private void bookActions(){
    new com.google.android.material.dialog.MaterialAlertDialogBuilder(root.getContext()).setTitle(book())
        .setItems(new String[]{"Chọn công thức","Đổi tên","Xóa cookbook (giữ công thức)"},(d,index)->
            CookbookDialog.create(index==0?"members":index==1?"rename":"delete",book())
                .show(getParentFragmentManager(),"book")).show();
  }
  private void render(){
    if(root==null||recipes==null)return;
    query=Ui.text(root,R.id.search);ingredient=Ui.text(root,R.id.ingredient_filter);
    favorite=((CheckBox)root.findViewById(R.id.favorites)).isChecked();
    boolean isBooks=!discover()&&book().isEmpty()&&showBooks&&query.trim().isEmpty();
    root.findViewById(R.id.recipe_filters).setVisibility(isBooks?View.GONE:View.VISIBLE);
    root.findViewById(R.id.manage_books).setSelected(isBooks);
    root.findViewById(R.id.all_recipes).setSelected(!isBooks);
    shown.clear(); String q=Units.key(query);
    Set<Long> selected=book().isEmpty()?Collections.emptySet():vm.repository.dao.recipeIdsForCookbook(book());
    for(Recipe r:all){
      if(!LibraryScope.includes(r,discover())||(!book().isEmpty()&&!selected.contains(r.id)))continue;
      String hay=Units.key(r.title+" "+r.tags+" "+r.ingredients);boolean ok=true;
      for(String token:q.split("[,\\s]+"))if(!hay.contains(token))ok=false;
      if(ok&&(!favorite||r.favorite)&&IngredientFilter.matches(ingredient,RecipeParser.ingredients(r.ingredients)))shown.add(r);
    }
    GridView grid=root.findViewById(R.id.list); BaseAdapter target=isBooks?bookAdapter:recipes;
    if(grid.getAdapter()!=target)grid.setAdapter(target);target.notifyDataSetChanged();
    int count=target.getCount();Ui.set(root,R.id.empty,!Boolean.TRUE.equals(vm.repository.ready.getValue())?"Đang tải…":
        count==0?(isBooks?"Chưa có cookbook. Nhấn + để tạo.":"Chưa có công thức phù hợp."):
        count+(isBooks?" cookbooks":" công thức"));
    if(!book().isEmpty()&&!vm.repository.dao.bookNamesSnapshot().contains(book())){
      root.findViewById(R.id.cookbook_options).setEnabled(false);
      Ui.set(root,R.id.empty,"Cookbook đã đổi tên hoặc bị xóa. Nhấn Back để về thư viện.");
    }
  }
  @Override public void onSaveInstanceState(Bundle out){
    if(root!=null){out.putString("query",Ui.text(root,R.id.search));out.putString("ingredient",Ui.text(root,R.id.ingredient_filter));
      out.putBoolean("favorite",((CheckBox)root.findViewById(R.id.favorites)).isChecked());}
    out.putBoolean("showBooks",showBooks);super.onSaveInstanceState(out);
  }
  @Override public void onDestroyView(){root=null;super.onDestroyView();}
}
